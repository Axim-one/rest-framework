package one.axim.framework.mybatis.plugin;

import one.axim.framework.core.data.XPage;
import one.axim.framework.core.data.XPagination;
import one.axim.framework.mybatis.model.XMapperParameter;
import one.axim.framework.mybatis.repository.XRepositoryConfig;
import one.axim.framework.mybatis.utils.ColumnSpec;
import one.axim.framework.mybatis.utils.XPaginationSql;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.scripting.xmltags.ForEachSqlNode;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
    private final Logger log = LoggerFactory.getLogger(XResultInterceptor.class);

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object intercept(Invocation invocation) throws Throwable {

        final Object[] queryArgs = invocation.getArgs();

        MappedStatement mappedStatement = (MappedStatement) queryArgs[MAPPED_STATEMENT_INDEX];
        Object parameter = queryArgs[PARAMETER_INDEX];

        log.debug("XResultInterceptor: Intercepting method: " + mappedStatement.getId());

        if (!mappedStatement.getSqlCommandType().name().equals("SELECT")) {
            if (XRepositoryConfig.isDebug()) {
                String query = getQueryString(mappedStatement, queryArgs[PARAMETER_INDEX]);
                log.info("XQUERY ==> \n" + query);
            }
            return invocation.proceed();
        }


        // Handle dynamic result type for XMapperParameter
        if (parameter instanceof XMapperParameter xMapperParameter) {
            ResultMap resultMap = mappedStatement.getResultMaps().get(0);
            if (!ColumnSpec.isNormalType(resultMap.getType())) {
                if (xMapperParameter.getResultClass() != null) {
                    MappedStatement newMs =
                            copyFromMappedStatement(mappedStatement, mappedStatement.getSqlSource(), resultMap,
                                    xMapperParameter.getResultClass());
                    queryArgs[MAPPED_STATEMENT_INDEX] = newMs;
                    mappedStatement = newMs; // Use the new MappedStatement
                }
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
                StringBuilder queryBuilder = new StringBuilder(query);
                queryBuilder.append(XPaginationSql.orderBy(pagination));
                queryBuilder.append(XPaginationSql.limit(pagination));

                queryArgs[ROWBOUNDS_INDEX] = new RowBounds(RowBounds.NO_ROW_OFFSET, RowBounds.NO_ROW_LIMIT);

                String limitOrderQuery = queryBuilder.toString();

                if (mappedStatement.getResultMaps().get(0).getType().equals(XPage.class)) {
                    Class resultType = parameterInResultType(parameter);

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
            setParameters(countStmt, mappedStatement, countBS, parameterObject);
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

    @SuppressWarnings("unchecked")
    public void setParameters(PreparedStatement ps, MappedStatement mappedStatement, BoundSql boundSql,
                              Object parameterObject) throws SQLException {

        ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());

        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

        if (parameterMappings != null) {

            Configuration configuration = mappedStatement.getConfiguration();

            TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();

            MetaObject metaObject = parameterObject == null ? null :
                    configuration.newMetaObject(parameterObject);

            for (int i = 0; i < parameterMappings.size(); i++) {

                ParameterMapping parameterMapping = parameterMappings.get(i);

                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    Object value;
                    String propertyName = parameterMapping.getProperty();
                    PropertyTokenizer prop = new PropertyTokenizer(propertyName);

                    if (parameterObject == null) {

                        value = null;
                    } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {

                        value = parameterObject;
                    } else if (boundSql.hasAdditionalParameter(propertyName)) {

                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (propertyName.startsWith(
                            ForEachSqlNode.ITEM_PREFIX) && boundSql.hasAdditionalParameter(prop.getName())) {

                        value = boundSql.getAdditionalParameter(prop.getName());

                        if (value != null) {
                            value = configuration.newMetaObject(value)
                                    .getValue(propertyName.substring(prop.getName().length()));
                        }
                    } else if (parameterObject instanceof Object[]) {

                        if(prop.getChildren() != null) {
                            Object obj = ((Map) ((Object[]) parameterObject)[1]).get(prop.getName());
                            value = getObjectFieldValue(obj, prop.getChildren());
                        }
                        else {
                            value = ((Map) ((Object[]) parameterObject)[1]).get(propertyName);
                        }
                    } else {

                        value = metaObject == null ? null : metaObject.getValue(propertyName);
                    }

                    TypeHandler typeHandler = parameterMapping.getTypeHandler();

                    if (typeHandler == null) {

                        throw new ExecutorException(
                                "There was no TypeHandler found for parameter " + propertyName + " of statement " +
                                        mappedStatement.getId());
                    }

                    typeHandler.setParameter(ps, i + 1, value, parameterMapping.getJdbcType());
                }
            }
        }
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
