package one.axim.framework.mybatis.proxy;

import one.axim.framework.mybatis.mapper.CommonMapper;
import one.axim.framework.mybatis.meta.EntityMetadata;
import one.axim.framework.mybatis.meta.EntityMetadataFactory;
import one.axim.framework.mybatis.model.XMapperParameter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class XRepositoryProxy implements InvocationHandler {

    private static final EntityMetadataFactory METADATA_FACTORY = new EntityMetadataFactory();
    private final CommonMapper commonMapper;
    private final Class<?> repositoryInterface;
    private final EntityMetadata entityMetadata;

    /** Method → parsed camelCase field names cache (e.g. findByPartnerIdAndStatus → ["partnerId", "status"]) */
    private final ConcurrentHashMap<Method, String[]> methodFieldCache = new ConcurrentHashMap<>();

    public XRepositoryProxy(CommonMapper commonMapper, Class<?> repositoryInterface, Class<?> keyClass, Class<?> modelClass) {
        this.commonMapper = commonMapper;
        this.repositoryInterface = repositoryInterface;
        this.entityMetadata = METADATA_FACTORY.getMetadata(modelClass, keyClass);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }

        // Handle standard CRUD methods
        switch (method.getName()) {
            case "save":
                return handleSave(args[0]);
            case "insert":
                return handleInsert(args[0]);
            case "saveAll":
                return handleSaveAll(args[0]);
            case "update":
                return handleUpdate(args[0]);
            case "modify":
                return handleModify(args[0]);
            case "delete":
            case "deleteById":
            case "remove":
                return handleRemove(args[0]);
            case "findOne":
                return handleFindOne(args[0]);
            case "findAll":
                if (args == null || args.length == 0) {
                    return handleFindAll();
                } else {
                    return handleFindAllPaginated(args[0], null);
                }
            case "exists":
                return handleExists(args[0]);
            case "count":
                if (args == null || args.length == 0) {
                    return handleCount();
                } else {
                    return handleCountWithWhere(args[0]);
                }
            case "findWhere":
                if (args.length == 1) {
                    return handleAllWhere(args[0]);
                } else {
                    return handleFindAllPaginated(args[0], args[1]);
                }
            case "findOneWhere":
                return handleWhere(args[0]);
            case "deleteWhere":
                return handleDeleteWhere(args[0]);
        }

        // Handle custom query derivation methods (longer prefixes checked first)
        if (method.getName().startsWith("findAllBy")) {
            return handleFindBy("findAllBy", method, args, false);
        } else if (method.getName().startsWith("findBy")) {
            boolean isList = method.getReturnType().isAssignableFrom(List.class);
            return handleFindBy("findBy", method, args, !isList);
        } else if (method.getName().startsWith("countBy")) {
            return handleCountBy(method, args);
        } else if (method.getName().startsWith("existsBy")) {
            return handleExistsBy(method, args);
        } else if (method.getName().startsWith("deleteBy")) {
            return handleDeleteBy(method, args);
        }

        throw new UnsupportedOperationException("Unsupported method: " + method.getName());
    }

    private Object handleSave(Object model) {
        try {
            if (entityMetadata.getPrimaryKeyColumns().size() > 1) {
                throw new UnsupportedOperationException(
                        "save() is not supported for composite primary keys. Use insert() or update() directly.");
            }
            Object pkValue = entityMetadata.getPrimaryKeyColumns().get(0).getPropertyDescriptor().getReadMethod().invoke(model);

            log.debug("db save primary key : {} ", pkValue);

            if (pkValue == null) {
                return handleInsert(model);
            }

            // Atomic upsert — INSERT ... ON DUPLICATE KEY UPDATE
            // Eliminates TOCTOU race between exists-check and insert/update
            XMapperParameter parameter = new XMapperParameter(model);
            parameter.setResultClass(entityMetadata.getModelClass());
            commonMapper.upsert(parameter);
            return pkValue;
        } catch (Exception e) {
            throw new RuntimeException("Could not determine save action", e);
        }
    }

    private Object handleInsert(Object model) {
        XMapperParameter parameter = new XMapperParameter(model);
        parameter.setResultClass(entityMetadata.getModelClass());
        commonMapper.insertAndSelectKey(parameter);

        if(entityMetadata.getPrimaryKeyColumns().get(0).isAutoIncrement()) { // Auto Increment 로 ID 가 생성 된다면 .

            Class<?> pkType = entityMetadata.getPrimaryKeyColumns().get(0).getPropertyDescriptor().getPropertyType();

            // Get the generated ID. It should be a Long, but could be an Integer from some drivers.
            Object generatedIdObj = parameter.getLastInsertedId();

            if(generatedIdObj != null) {

                if (generatedIdObj instanceof Number) {

                    Number generatedId = (Number) generatedIdObj;

                    try {
                        Object convertedId;

                        // Convert the retrieved Number ID to the actual type of the model's primary key
                        if (pkType.equals(Long.class) || pkType.equals(long.class)) {
                            convertedId = generatedId.longValue();
                        } else if (pkType.equals(Integer.class) || pkType.equals(int.class)) {
                            convertedId = generatedId.intValue();
                        } else {
                            // For other numeric types, we might need more specific handling.
                            // For now, we return the long value as a safe default for auto-increment keys.
                            convertedId = generatedId.longValue();
                        }

                        if(entityMetadata.getPrimaryKeyColumns().get(0).isAutoIncrement()) {
                            // Set the ID on the model object
                            entityMetadata.getPrimaryKeyColumns().get(0).getPropertyDescriptor().getWriteMethod().invoke(model, convertedId);
                        }

                        // Return the ID, now correctly typed
                        return convertedId;
                    } catch (Exception e) {
                        throw new RuntimeException("Could not set ID on model after insert", e);
                    }
                }
            }
        }

        // Non-auto-increment: return the PK value from the model itself
        try {
            return entityMetadata.getPrimaryKeyColumns().get(0)
                    .getPropertyDescriptor().getReadMethod().invoke(model);
        } catch (Exception e) {
            throw new RuntimeException("Could not read PK from model after insert", e);
        }
    }

    private Object handleSaveAll(Object models) {
        if (!(models instanceof Iterable)) {
            throw new IllegalArgumentException("Argument for saveAll must be an Iterable.");
        }

        List<Object> modelList = new ArrayList<>();
        ((Iterable<?>) models).forEach(modelList::add);

        if (modelList.isEmpty()) {
            return 0; // Return 0 for empty list
        }

        XMapperParameter insertParameter = new XMapperParameter(modelList);
        insertParameter.setResultClass(entityMetadata.getModelClass());
        return commonMapper.insertAll(insertParameter);
    }

    private Object handleUpdate(Object model) {
        XMapperParameter parameter = new XMapperParameter(model);
        parameter.setResultClass(entityMetadata.getModelClass());
        return commonMapper.update(parameter);
    }

    private Object handleModify(Object model) {
        XMapperParameter parameter = new XMapperParameter(model);
        parameter.setResultClass(entityMetadata.getModelClass());
        return commonMapper.selectiveUpdate(parameter);
    }

    private Object handleRemove(Object key) {
        XMapperParameter parameter = new XMapperParameter();
        parameter.setWhere(key);
        parameter.setResultClass(entityMetadata.getModelClass());
        return commonMapper.delete(parameter);
    }

    private Object handleFindOne(Object key) {
        XMapperParameter parameter = new XMapperParameter();
        parameter.setWhere(key);
        parameter.setResultClass(entityMetadata.getModelClass());
        return commonMapper.findById(parameter);
    }

    private Object handleFindAll() {
        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        return commonMapper.findAll(parameter);
    }

    private Object handleFindAllPaginated(Object pagination, Object where) {
        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        parameter.setPagination((one.axim.framework.core.data.XPagination) pagination);

        if (where != null) {
            parameter.setWhere(where);
        }

        java.util.List<Object> result = commonMapper.findAll(parameter);
        if (result != null && !result.isEmpty()) {
            Object first = result.get(0);
            if (first instanceof one.axim.framework.core.data.XPage) {
                return first;
            }
            // Interceptor not applied: wrap results in XPage
            one.axim.framework.core.data.XPage<Object> page = new one.axim.framework.core.data.XPage<>();
            page.setPageRows(result);
            return page;
        }
        return new one.axim.framework.core.data.XPage<>();
    }

    private Object handleExists(Object key) {
        return handleFindOne(key) != null;
    }

    private Object handleCount() {
        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        return commonMapper.count(parameter);
    }

    private Object handleCountWithWhere(Object where) {
        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        parameter.setWhere(where);
        return commonMapper.count(parameter);
    }

    private long handleCountBy(Method method, Object[] args) {
        Map<String, Object> whereMap = buildWhereMap(method, "countBy", args);

        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        parameter.setWhere(whereMap);
        return commonMapper.count(parameter);
    }

    private Object handleDeleteBy(Method method, Object[] args) {
        Map<String, Object> whereMap = buildWhereMap(method, "deleteBy", args);

        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        parameter.setWhere(whereMap);
        return commonMapper.delete(parameter);
    }

    private boolean handleExistsBy(Method method, Object[] args) {
        Map<String, Object> whereMap = buildWhereMap(method, "existsBy", args);

        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        parameter.setWhere(whereMap);
        long count = commonMapper.count(parameter);
        return count > 0;
    }

    private Object handleFindBy(String prefixStr, Method method, Object[] args, boolean isOne) {
        Map<String, Object> whereMap = buildWhereMap(method, prefixStr, args);

        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        parameter.setWhere(whereMap);

        if (isOne)
            return commonMapper.findOneBy(parameter);
        else
            return commonMapper.findWhere(parameter);
    }

    // ──────────────────────────────────────────
    // Method name parsing cache
    // ──────────────────────────────────────────

    /**
     * Resolves and caches the camelCase field names parsed from a derived method name.
     * e.g. findByPartnerIdAndStatus → ["partnerId", "status"] (parsed once, cached forever)
     */
    /**
     * Splits on "And" only when it appears between two upper-case-starting segments.
     * e.g. "PartnerIdAndStatus" → ["PartnerId", "Status"]
     *      "BrandName"          → ["BrandName"]  (no false split)
     */
    private String[] resolveFieldNames(Method method, String prefix) {
        return methodFieldCache.computeIfAbsent(method, m -> {
            String searchPart = m.getName().substring(prefix.length());
            String[] rawFields = searchPart.split("(?<=[a-z0-9])And(?=[A-Z])");
            String[] fields = new String[rawFields.length];
            for (int i = 0; i < rawFields.length; i++) {
                fields[i] = Character.toLowerCase(rawFields[i].charAt(0)) + rawFields[i].substring(1);
            }
            return fields;
        });
    }

    /**
     * Builds whereMap by zipping cached field names with runtime argument values.
     */
    private Map<String, Object> buildWhereMap(Method method, String prefix, Object[] args) {
        String[] fieldNames = resolveFieldNames(method, prefix);

        if (args == null || args.length != fieldNames.length) {
            throw new IllegalArgumentException(
                    "Invalid arguments for " + method.getName() +
                    ". Expected " + fieldNames.length + " arguments, but got " + (args == null ? 0 : args.length) + ".");
        }

        Map<String, Object> whereMap = new HashMap<>(fieldNames.length);
        for (int i = 0; i < fieldNames.length; i++) {
            whereMap.put(fieldNames[i], args[i]);
        }
        return whereMap;
    }

    @Override
    public String toString() {
        return "XRepositoryProxy{interface=" + repositoryInterface.getSimpleName()
                + ", entity=" + entityMetadata.getModelClass().getSimpleName() + "}";
    }

    private Object handleWhere(Object where) {
        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        parameter.setWhere(where);
        return commonMapper.findOneBy(parameter);
    }

    private Object handleDeleteWhere(Object where) {
        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        parameter.setWhere(where);
        return commonMapper.delete(parameter);
    }

    private Object handleAllWhere(Object where) {
        XMapperParameter parameter = new XMapperParameter();
        parameter.setResultClass(entityMetadata.getModelClass());
        parameter.setWhere(where);
        return commonMapper.findWhere(parameter);
    }
}
