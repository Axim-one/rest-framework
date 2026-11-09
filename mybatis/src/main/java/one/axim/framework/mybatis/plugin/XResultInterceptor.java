package one.axim.framework.mybatis.plugin;

import one.axim.framework.core.data.XPage;
import one.axim.framework.core.data.XPagination;
import one.axim.framework.mybatis.dialect.XDialect;
import one.axim.framework.mybatis.dialect.XDialectHolder;
import one.axim.framework.mybatis.mapper.CommonMapper;
import one.axim.framework.mybatis.meta.ColumnMetadata;
import one.axim.framework.mybatis.meta.EntityMetadataFactory;
import one.axim.framework.mybatis.model.XMapperParameter;
import one.axim.framework.mybatis.repository.XRepositoryConfig;
import one.axim.framework.mybatis.utils.ColumnSpec;
import one.axim.framework.mybatis.utils.XPaginationSql;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by dudgh on 2017. 5. 30..
 */
@Intercepts(
        {@Signature(type = Executor.class, method = "query", args =
                {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
                @Signature(type = Executor.class, method = "update", args =
                        {MappedStatement.class, Object.class})})
public class XResultInterceptor implements Interceptor {

    private static final int MAPPED_STATEMENT_INDEX = 0;
    private static final int PARAMETER_INDEX = 1;
    private static final int ROWBOUNDS_INDEX = 2;
    private static final int RESULT_HANDLER_INDEX = 3;
    static final java.time.format.DateTimeFormatter dtFormat =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, Class<?>> RETURN_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> GENERIC_RETURN_CACHE = new ConcurrentHashMap<>();
    /**
     * PostgreSQL 용으로 키 생성기를 바꾼 MappedStatement 캐시 (v1.5.0).
     * 원본 MappedStatement 를 identity 로 키에 넣는다 — Configuration(SqlSessionFactory)이 여럿이어도 섞이지 않는다.
     */
    private static final Map<PgKeyCacheKey, MappedStatement> PG_KEY_STATEMENT_CACHE = new ConcurrentHashMap<>();

    private record PgKeyCacheKey(MappedStatement original, String keyColumn) {}
    private static final String COMMON_MAPPER_PREFIX = CommonMapper.class.getName() + ".";
    private static final String INSERT_AND_SELECT_KEY_ID = COMMON_MAPPER_PREFIX + "insertAndSelectKey";
    private static final EntityMetadataFactory METADATA_FACTORY = new EntityMetadataFactory();
    private final Logger log = LoggerFactory.getLogger(XResultInterceptor.class);

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object intercept(Invocation invocation) throws Throwable {

        final Object[] queryArgs = invocation.getArgs();

        MappedStatement mappedStatement = (MappedStatement) queryArgs[MAPPED_STATEMENT_INDEX];
        Object parameter = queryArgs[PARAMETER_INDEX];

        log.debug("XResultInterceptor: Intercepting method: " + mappedStatement.getId());

        resolveDialectIfAbsent(invocation);

        if (!mappedStatement.getSqlCommandType().name().equals("SELECT")) {
            if (XDialectHolder.current().usesJdbcGeneratedKeys()) {
                MappedStatement adapted = adaptKeyGeneration(mappedStatement, parameter);
                if (adapted != mappedStatement) {
                    queryArgs[MAPPED_STATEMENT_INDEX] = adapted;
                    mappedStatement = adapted;
                }
            }
            if (XRepositoryConfig.isDebug()) {
                String query = getQueryString(mappedStatement, queryArgs[PARAMETER_INDEX]);
                log.info("XQUERY ==> \n" + query);
            }
            return invocation.proceed();
        }


        // Handle dynamic result type for XMapperParameter.
        //
        // The caller-declared resultClass (XMapperParameter.resultClass) must take
        // precedence over MyBatis's inferred ResultMap type whenever:
        //   (a) caller declared a non-scalar entity class as the expected result, AND
        //   (b) the mapper method's declared return type is generic (<T> T / <T> List<T>),
        //       i.e. erased to Object or Collection at runtime.
        //
        // Without clause (b) we would also override scalar-returning methods like
        // `long count(XMapperParameter)` — CommonMapper.count() intentionally has a
        // concrete `long` return type, and callers still set resultClass on the
        // parameter because CrudSqlProvider needs it to derive the table name.
        //
        // Without clause (a) we would attempt to map a SELECT result to a scalar,
        // which is never the intent of setResultClass().
        if (parameter instanceof XMapperParameter xMapperParameter) {
            ResultMap resultMap = mappedStatement.getResultMaps().get(0);
            Class<?> declaredResult = xMapperParameter.getResultClass();
            Class<?> inferredResult = resultMap.getType();

            boolean needsSwap =
                    declaredResult != null
                            && inferredResult != declaredResult
                            && !ColumnSpec.isNormalType(declaredResult)
                            && mapperReturnsGenericType(mappedStatement.getId());

            if (needsSwap) {
                MappedStatement newMs =
                        copyFromMappedStatement(mappedStatement, mappedStatement.getSqlSource(), resultMap,
                                declaredResult);
                queryArgs[MAPPED_STATEMENT_INDEX] = newMs;
                mappedStatement = newMs; // Use the new MappedStatement
            }
        }

        // Handle pagination
        XPagination pagination = parameterInPagination(parameter);

        if (pagination != null) {
            log.debug("XResultInterceptor: Found XPagination parameter, applying pagination.");

            Map<String, Object> mapParam = originParameterMap(parameter);
            Object paramForBoundSql = (mapParam != null) ? mapParam : queryArgs[PARAMETER_INDEX];

            final BoundSql boundSql = mappedStatement.getBoundSql(paramForBoundSql);
            String query = boundSql.getSql().trim().replaceAll(";$", "");

            Executor executor = (Executor) invocation.getTarget();
            Connection connection = executor.getTransaction().getConnection();
            int total = getCount(query, connection, mappedStatement, paramForBoundSql, boundSql);

            if (total > 0) {
                // 호출자 SQL 에 이미 최상위 ORDER BY 가 있는데 명시 정렬까지 덧붙이면
                //   "... ORDER BY a DESC ORDER BY b DESC LIMIT 20" 이 되어 SQL 문법 오류가 난다.
                //   명시 정렬이 있으면 호출자 ORDER BY 를 제거하고 대체한다(사용자 정렬 우선).
                //   명시 정렬이 없으면 호출자 ORDER BY 를 그대로 존중한다. (v1.4.1)
                String orderClause = XPaginationSql.orderBy(pagination);
                String baseQuery = orderClause.isEmpty()
                        ? query
                        : XPaginationSql.stripTrailingOrderBy(query);

                StringBuilder queryBuilder = new StringBuilder(baseQuery);
                queryBuilder.append(orderClause);
                queryBuilder.append(XPaginationSql.limit(pagination));

                queryArgs[ROWBOUNDS_INDEX] = new RowBounds(RowBounds.NO_ROW_OFFSET, RowBounds.NO_ROW_LIMIT);

                String limitOrderQuery = queryBuilder.toString();

                if (mappedStatement.getResultMaps().get(0).getType().equals(XPage.class)) {
                    Class resultType = parameterInResultType(parameter);

                    if (resultType == null) {
                        resultType = resolveReturnTypeFromMethod(mappedStatement.getId());
                    }

                    if (resultType == null) {
                        log.error("Not found Result Parameter Class ");
                    }

                    queryArgs[MAPPED_STATEMENT_INDEX] =
                            copyFromNewSql(mappedStatement, boundSql, limitOrderQuery, resultType);
                } else {
                    queryArgs[MAPPED_STATEMENT_INDEX] =
                            copyFromNewSql(mappedStatement, boundSql, limitOrderQuery, null);
                }

                if (XRepositoryConfig.isDebug()) {
                    log.info("QUERY====>\n\n" + limitOrderQuery + "\n");
                }

                Object result = invocation.proceed();

                XPage<Object> page = new XPage<>();
                page.setTotalCount(total);
                page.setOffset(pagination.getOffset());
                page.setSize(pagination.getSize());
                page.setOrders(pagination.getOrders());
                page.setPageRowsByObject(result);
                page.setPage(pagination.getPage());

                List<XPage<?>> tmp = new ArrayList<>(1);
                tmp.add(page);
                return tmp;
            } else {
                XPage<Object> page = new XPage<>();
                page.setTotalCount(total);
                page.setOffset(pagination.getOffset());
                page.setSize(pagination.getSize());
                page.setOrders(pagination.getOrders());
                page.setPageRowsByObject(new ArrayList<>()); // empty list
                page.setPage(pagination.getPage());

                List<XPage<?>> tmp = new ArrayList<>(1);
                tmp.add(page);
                return tmp;
            }
        }

        if (XRepositoryConfig.isDebug()) {
            String query = getQueryString(mappedStatement, queryArgs[PARAMETER_INDEX]);
            log.info("XQUERY ==> \n" + query);
        }

        return invocation.proceed();
    }

    /**
     * 기동 시 방언이 정해지지 않았으면(설정 없음 + DataSource 감지 실패) 지금 쓰는 커넥션으로
     * 감지한다. 그래도 모르면 MySQL 로 고정. (v1.5.0)
     */
    private void resolveDialectIfAbsent(Invocation invocation) {
        if (XDialectHolder.isResolved()) {
            return;
        }
        XDialect dialect = XDialectHolder.resolveIfAbsent(() -> {
            try {
                Executor executor = (Executor) invocation.getTarget();
                return XDialect.detect(executor.getTransaction().getConnection());
            } catch (SQLException | RuntimeException e) {
                log.warn("Axim mybatis dialect detection failed; falling back to mysql", e);
                return null;
            }
        });
        log.info("Axim mybatis dialect: {} (detected on first query)", dialect);
    }

    /**
     * PostgreSQL 에서 CommonMapper insert 계열의 키 생성을 바꾼다. (v1.5.0)
     *
     * <ul>
     *   <li>{@code insertAndSelectKey}: {@code @SelectKey("SELECT LAST_INSERT_ID()")} 는 PG 에 없다 →
     *       {@link Jdbc3KeyGenerator} + keyColumn = 자동증가 PK 컬럼. PG 드라이버가 {@code RETURNING <pk>} 를
     *       붙이고 값은 기존과 같이 {@code XMapperParameter.lastInsertedId} 로 들어간다.
     *       자동증가 PK 가 없으면 키 회수 없음.</li>
     *   <li>그 밖의 CommonMapper insert({@code insert}/{@code upsert}/{@code insertAll}): 전역
     *       {@code useGeneratedKeys=true} 때문에 keyProperty 없는 Jdbc3KeyGenerator 가 붙어 있으면
     *       PG 드라이버가 {@code RETURNING *} 를 붙이므로 키 회수를 끈다(쓰지 않는 값이다).</li>
     * </ul>
     * MySQL 경로는 이 메서드를 타지 않는다.
     */
    private MappedStatement adaptKeyGeneration(MappedStatement ms, Object parameter) {
        String id = ms.getId();
        if (!id.startsWith(COMMON_MAPPER_PREFIX)) {
            return ms;
        }
        KeyGenerator keyGenerator = ms.getKeyGenerator();

        if (INSERT_AND_SELECT_KEY_ID.equals(id) && keyGenerator instanceof SelectKeyGenerator) {
            if (!(parameter instanceof XMapperParameter xmp) || xmp.getResultClass() == null) {
                return ms;
            }
            String keyColumn = autoIncrementColumn(xmp.getResultClass());
            return PG_KEY_STATEMENT_CACHE.computeIfAbsent(new PgKeyCacheKey(ms, keyColumn), k -> keyColumn == null
                    ? copyWithKeyGenerator(ms, NoKeyGenerator.INSTANCE, null, null)
                    : copyWithKeyGenerator(ms, Jdbc3KeyGenerator.INSTANCE, "lastInsertedId", keyColumn));
        }

        if (keyGenerator instanceof Jdbc3KeyGenerator
                && (ms.getKeyProperties() == null || ms.getKeyProperties().length == 0)) {
            return PG_KEY_STATEMENT_CACHE.computeIfAbsent(new PgKeyCacheKey(ms, null),
                    k -> copyWithKeyGenerator(ms, NoKeyGenerator.INSTANCE, null, null));
        }
        return ms;
    }

    private String autoIncrementColumn(Class<?> entityClass) {
        for (ColumnMetadata column : METADATA_FACTORY.getMetadata(entityClass, null).getPrimaryKeyColumns()) {
            if (column.isAutoIncrement()) {
                return column.getColumnName();
            }
        }
        return null;
    }

    private MappedStatement copyWithKeyGenerator(MappedStatement ms, KeyGenerator keyGenerator,
                                                 String keyProperty, String keyColumn) {
        MappedStatement.Builder builder =
                new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), ms.getSqlSource(), ms.getSqlCommandType());
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.timeout(ms.getTimeout());
        builder.statementType(ms.getStatementType());
        builder.resultSetType(ms.getResultSetType());
        builder.parameterMap(ms.getParameterMap());
        builder.resultMaps(ms.getResultMaps());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        builder.resultOrdered(ms.isResultOrdered());
        builder.databaseId(ms.getDatabaseId());
        builder.lang(ms.getLang());
        builder.keyGenerator(keyGenerator);
        builder.keyProperty(keyProperty);
        builder.keyColumn(keyColumn);
        return builder.build();
    }

    public int getCount(final String sql, final Connection connection,
                        final MappedStatement mappedStatement, final Object parameterObject,
                        final BoundSql boundSql) throws SQLException {

        final String countSql = getCountString(sql);

        if (XRepositoryConfig.isDebug()) {
            log.info("XQuery ==> \n" + countSql);
        }

        PreparedStatement countStmt = null;
        ResultSet rs = null;
        try {
            countStmt = connection.prepareStatement(countSql);
            final BoundSql countBS = new BoundSql(mappedStatement.getConfiguration(), countSql,
                    boundSql.getParameterMappings(), parameterObject);
            mappedStatement.getConfiguration()
                    .newParameterHandler(mappedStatement, parameterObject, countBS)
                    .setParameters(countStmt);
            rs = countStmt.executeQuery();
            int count = 0;
            if (rs.next()) {
                count = rs.getInt(1);
            }
            return count;
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (countStmt != null) {
                countStmt.close();
            }
        }
    }

    private String getCountString(String query) {

        return "SELECT COUNT(1) FROM (" + query + ") _tmp";
    }

    private MappedStatement copyFromNewSql(MappedStatement ms,
                                           BoundSql boundSql, String sql,
                                           Class resultType) {

        BoundSql newBoundSql = copyFromBoundSql(ms, boundSql, sql);

        if (resultType == null) {
            return copyFromMappedStatement(ms, new BoundSqlSqlSource(newBoundSql));
        } else {
            return copyFromMappedStatement(ms, new BoundSqlSqlSource(newBoundSql), ms.getResultMaps().get(0),
                    resultType);
        }
    }

    public BoundSql copyFromBoundSql(MappedStatement ms,
                                     BoundSql boundSql, String sql) {

        BoundSql newBoundSql = new BoundSql(ms.getConfiguration(), sql,
                boundSql.getParameterMappings(),
                boundSql.getParameterObject());
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            String prop = mapping.getProperty();
            if (boundSql.hasAdditionalParameter(prop)) {
                newBoundSql.setAdditionalParameter(prop,
                        boundSql.getAdditionalParameter(prop));
            }
        }
        return newBoundSql;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private MappedStatement copyFromMappedStatement(MappedStatement ms, SqlSource newSqlSource) {

        MappedStatement.Builder builder =
                new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), newSqlSource, ms.getSqlCommandType());

        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        String[] keyProperties = ms.getKeyProperties();
        builder.keyProperty(keyProperties == null ? null : String.join(",", keyProperties));

        //setStatementTimeout()
        builder.timeout(ms.getTimeout());

        //setStatementResultMap()
        builder.parameterMap(ms.getParameterMap());

        //setStatementResultMap()
        builder.resultMaps(ms.getResultMaps());
        builder.resultSetType(ms.getResultSetType());

        //setStatementCache()
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());

        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private MappedStatement copyFromMappedStatement(MappedStatement ms, SqlSource newSqlSource, ResultMap resultMap,
                                                    Class resultCls) {

        MappedStatement.Builder builder =
                new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), newSqlSource, ms.getSqlCommandType());

        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());

        if (ms.getKeyProperties() != null && ms.getKeyProperties().length != 0) {
            builder.keyProperty(String.join(",", ms.getKeyProperties()));
        }

        //setStatementTimeout()
        builder.timeout(ms.getTimeout());

        //setStatementResultMap()
        builder.parameterMap(ms.getParameterMap());

        //setStatementResultMap()
        List<ResultMap> newResultMaps = new ArrayList<>();
        newResultMaps.add(new ResultMap.Builder(ms.getConfiguration(),
                resultMap.getId(), resultCls,
                resultMap.getResultMappings()).build());

        newResultMaps.add(resultMap);

        builder.resultMaps(newResultMaps);


        builder.resultSetType(ms.getResultSetType());

        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());

        return builder.build();
    }

    @Override
    public Object plugin(Object target) {

        if (Executor.class.isAssignableFrom(target.getClass())) {
            return Plugin.wrap(target, this);
        }

        return target;
    }

    @Override
    public void setProperties(Properties properties) {

    }

    private XPagination parameterInPagination(Object params) {

        if (params == null) {
            return null;
        }

        log.debug("Parameter type: " + params.getClass().getName());

        if (params instanceof XMapperParameter xmp) {
            return xmp.getPagination();
        } else if (params instanceof MapperMethod.ParamMap<?> paramMap) {
            for (Object paramValue : paramMap.values()) {
                if (paramValue instanceof XPagination xp) {
                    return xp;
                }
                if (paramValue instanceof XMapperParameter xmp) {
                    return xmp.getPagination();
                }
            }
        }

        return null;
    }

    private Map<String, Object> originParameterMap(Object params) {

        if (params == null) {
            return null;
        }

        if (params instanceof MapperMethod.ParamMap<?> paramMap) {
            for (Object paramValue : paramMap.values()) {
                if (paramValue instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
            }
        }

        return null;
    }

    private Class<?> parameterInResultType(Object params) {

        if (params == null) {
            return null;
        }

        if (Class.class.isAssignableFrom(params.getClass())) {
            return (Class<?>) params;
        } else if (params instanceof MapperMethod.ParamMap<?> paramMap) {
            for (Object paramValue : paramMap.values()) {
                if (paramValue instanceof Class<?> cls) {
                    return cls;
                }
            }
        }

        return null;
    }

    /**
     * MappedStatement ID(className.methodName)에서 메서드 반환 타입의 제네릭 파라미터를 추출합니다.
     * 예: XPage<User> → User.class
     * Class<?> 파라미터 없이 커스텀 @Mapper에서 XPage 페이지네이션을 사용할 수 있게 합니다.
     */
    private Class<?> resolveReturnTypeFromMethod(String msId) {

        return RETURN_TYPE_CACHE.computeIfAbsent(msId, id -> {
            try {
                int lastDot = id.lastIndexOf('.');
                if (lastDot < 0) return null;

                String className = id.substring(0, lastDot);
                String methodName = id.substring(lastDot + 1);

                Class<?> mapperClass = Class.forName(className);
                for (Method method : mapperClass.getMethods()) {
                    if (method.getName().equals(methodName)) {
                        Type returnType = method.getGenericReturnType();
                        if (returnType instanceof ParameterizedType pt && pt.getRawType() == XPage.class) {
                            Type actualType = pt.getActualTypeArguments()[0];
                            if (actualType instanceof Class<?> cls) {
                                return cls;
                            }
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                log.warn("Failed to resolve return type for mapper method: {}", id, e);
            }
            return null;
        });
    }

    /**
     * mapper 메서드의 선언된 반환 타입이 제네릭으로 erasure 된 Object/Collection 인지 판정.
     *
     * <p>findById: {@code <T> T findById(...)} → Object → true
     * <p>findAll:  {@code <T> List<T> findAll(...)} → List → true
     * <p>count:    {@code long count(...)} → long → false
     *
     * <p>이 구분이 필요한 이유는 warm-path 캐시 오염 상황에서 findById 의
     * {@code ResultMap.getType()} 이 scalar (Long) 로 잘못 고정되는 케이스를
     * {@code XMapperParameter.resultClass} 로 덮어써야 하기 때문이며,
     * 반대로 count 같은 concrete scalar 반환 메서드에서는 덮어쓰면 안 되기 때문.
     */
    private boolean mapperReturnsGenericType(String msId) {

        return GENERIC_RETURN_CACHE.computeIfAbsent(msId, id -> {
            try {
                int lastDot = id.lastIndexOf('.');
                if (lastDot < 0) return false;

                String className = id.substring(0, lastDot);
                String methodName = id.substring(lastDot + 1);

                Class<?> mapperClass = Class.forName(className);
                for (Method method : mapperClass.getMethods()) {
                    if (method.getName().equals(methodName)) {
                        Class<?> raw = method.getReturnType();
                        return raw == Object.class || Collection.class.isAssignableFrom(raw);
                    }
                }
            } catch (ClassNotFoundException e) {
                log.warn("Failed to resolve mapper return type: {}", id, e);
            }
            return false;
        });
    }

    private String getQueryString(MappedStatement mappedStatement, Object parameters) {
        HashMap<String, Object> parameterValues = new HashMap<>();

        BoundSql boundSql = mappedStatement.getBoundSql(parameters);

        Object oneValue = null;

        if (parameters != null) {
            if (ColumnSpec.isNormalType(parameters.getClass())) { // one params
                // nothing ...
                oneValue = parameters;
            } else if (parameters instanceof Map) {
                mapToMap(parameterValues, null, (Map) parameters);
            } else {
                objectToMap(parameterValues, null, parameters);
            }
        }

        String query = boundSql.getSql();

        if (oneValue != null) {
            query = query.replaceAll("\\?", parameterToString(parameterValues.get(oneValue)));
        } else {
            if (parameterValues.size() > 0) {
                StringBuilder sb = new StringBuilder();

                Pattern pattern = Pattern.compile("(\\?)");
                Matcher matcher = pattern.matcher(query);

                int index = 0;
                int pos = 0;

                while (matcher.find()) {
                    int start = matcher.start();
                    sb.append(query.substring(pos, start));

                    ParameterMapping mapping = boundSql.getParameterMappings().get(index++);
                    sb.append(parameterToString(parameterValues.get(mapping.getProperty())));

                    pos = matcher.end();
                }

                if (sb.length() > 0) {
                    if (pos < query.length()) {
                        sb.append(query.substring(pos, query.length()));
                    }
                    query = sb.toString();
                }
            }
        }

        return query;
    }

    private void mapToMap(HashMap<String, Object> valueMap, String parentKey, Map<String, ?> map) {

        Set<String> keys = map.keySet();

        for (String key : keys) {
            String newKey = (parentKey == null ? "" : parentKey + ".") + key;
            if (map.get(key) != null) {
                if (ColumnSpec.isNormalType(map.get(key).getClass())) { // Value
                    valueMap.put(newKey, map.get(key));
                } else if (map.get(key) instanceof Map) {
                    mapToMap(valueMap, newKey, (Map<String, ?>) map.get(key));
                } else {
                    objectToMap(valueMap, newKey, map.get(key));
                }
            } else {
                valueMap.put(newKey, map.get(key));
            }
        }
    }

    private void objectToMap(HashMap<String, Object> valueMap, String parentKey, Object params) {

        for (Field field : getAllFields(params.getClass())) {
            String newKey = (parentKey == null ? "" : parentKey + ".") + field.getName();
            try {

                PropertyDescriptor propertyDescriptor =
                        BeanUtils.getPropertyDescriptor(params.getClass(), field.getName());

                if (propertyDescriptor != null) {
                    Method getterMethod =
                            propertyDescriptor.getReadMethod();
                    if (getterMethod != null) {
                        Object value = getterMethod.invoke(params);

                        if (value != null && !(value instanceof Class)) {
                            if (ColumnSpec.isNormalType(value.getClass())) {
                                valueMap.put(newKey, value);
                            } else if (value instanceof Enum) {
                                valueMap.put(newKey, ((Enum) value).toString());
                            } else if (value instanceof Map) {
                                mapToMap(valueMap, newKey, (Map<String, ?>) value);
                            } else {
                                objectToMap(valueMap, newKey, value);
                            }
                        }
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                log.warn("Failed to read field value from {}: {}", params.getClass().getSimpleName(), field.getName(), e);
            }
        }
    }

    private Object getObjectFieldValue(Object obj, String fieldName) {

        try {
            PropertyDescriptor propertyDescriptor =
                    BeanUtils.getPropertyDescriptor(obj.getClass(), fieldName);

            if(propertyDescriptor != null) {
                return getValueByReadMethod(obj, propertyDescriptor.getReadMethod());
            }
        } catch (Exception e) {
            throw new RuntimeException("Field " + fieldName + " Object Reflection Exception ", e);
        }

        return null;
    }

    private String parameterToString(Object value) {

        if (value == null) {
            return "null";
        }

        if (value instanceof String s) {
            return "'" + s.replace("'", "''") + "'";
        } else if (value instanceof Date d) {
            return "'" + dtFormat.format(d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()) + "'";
        } else if (value instanceof Number) {
            return value.toString();
        } else if (value instanceof Boolean b) {
            return b ? "1" : "0";
        } else if (value instanceof Enum<?> e) {
            return e.toString();
        }

        return "" + value;
    }

    public static class BoundSqlSqlSource implements SqlSource {

        BoundSql boundSql;

        public BoundSqlSqlSource(BoundSql boundSql) {

            this.boundSql = boundSql;
        }

        public BoundSql getBoundSql(Object parameterObject) {

            return boundSql;
        }
    }

    private Object getValueByReadMethod(Object obj, Method getterMethod) throws InvocationTargetException, IllegalAccessException {

        if (getterMethod == null) {
            return null;
        }

        Object value = getterMethod.invoke(obj);

        if (value == null || value instanceof Class) {
            return null;
        }

        if (ColumnSpec.isNormalType(value.getClass())) {
            return value;
        } else if (value instanceof Enum<?> e) {
            return e.toString();
        } else if (value instanceof Map) {
            return (Map<String, ?>) value;
        }

        return value;
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                fields.add(field);
            }
        }
        return fields;
    }
}
