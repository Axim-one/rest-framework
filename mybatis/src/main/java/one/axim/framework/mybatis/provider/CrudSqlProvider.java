package one.axim.framework.mybatis.provider;

import one.axim.framework.mybatis.meta.ColumnMetadata;
import one.axim.framework.mybatis.meta.EntityMetadata;
import one.axim.framework.mybatis.meta.EntityMetadataFactory;
import one.axim.framework.mybatis.model.XMapperParameter;
import org.apache.ibatis.jdbc.SQL;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CrudSqlProvider {

    private static final EntityMetadataFactory METADATA_FACTORY = new EntityMetadataFactory();
    private static final ConcurrentHashMap<String, String> SQL_CACHE = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────
    // Public provider methods (called by MyBatis)
    // ──────────────────────────────────────────

    public String insert(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "insert"), k -> buildInsert(parameter));
    }

    public String insertAll(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "insertAll"), k -> buildInsertAll(parameter));
    }

    public String update(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "update"), k -> buildUpdate(parameter));
    }

    public String selectiveUpdate(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "selectiveUpdate"), k -> buildSelectiveUpdate(parameter));
    }

    public String delete(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "delete"), k -> buildDelete(parameter));
    }

    public String findById(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "findById"), k -> buildFindById(parameter));
    }

    public String findAll(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "findAll"), k -> buildFindAll(parameter));
    }

    public String findWhere(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "findWhere"), k -> buildFindWhere(parameter));
    }

    public String count(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "count"), k -> buildCount(parameter));
    }

    public String findOneBy(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "findOneBy"), k -> buildFindOneBy(parameter));
    }

    public String upsert(XMapperParameter parameter) {
        return SQL_CACHE.computeIfAbsent(
                cacheKey(parameter, "upsert"), k -> buildUpsert(parameter));
    }

    // ──────────────────────────────────────────
    // SQL build methods (called once per unique key)
    // ──────────────────────────────────────────

    private String buildInsert(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);
        return new SQL() {{
            INSERT_INTO(metadata.getTableName());
            for (ColumnMetadata column : metadata.getInsertableColumns()) {
                if (column.isAutoIncrement()) continue;
                if (column.isDBDefaultUsed() && column.resolveInsertValue() == null) continue;

                String value = column.resolveInsertValue();
                VALUES(column.getColumnName(), value != null ? value : "#{model." + column.getFieldName() + "}");
            }
        }}.toString();
    }

    /**
     * Builds INSERT IGNORE for batch inserts.
     * Rows with duplicate keys are silently skipped (no error thrown).
     */
    private String buildInsertAll(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);
        String columns = metadata.getInsertableColumns().stream()
                .filter(c -> !c.isAutoIncrement())
                .filter(c -> !(c.isDBDefaultUsed() && c.resolveInsertValue() == null))
                .map(ColumnMetadata::getColumnName)
                .collect(Collectors.joining(", "));

        String values = metadata.getInsertableColumns().stream()
                .filter(c -> !c.isAutoIncrement())
                .filter(c -> !(c.isDBDefaultUsed() && c.resolveInsertValue() == null))
                .map(c -> {
                    String v = c.resolveInsertValue();
                    return v != null ? v : "#{item." + c.getFieldName() + "}";
                })
                .collect(Collectors.joining(", "));

        return "<script>"
                + "INSERT IGNORE INTO " + metadata.getTableName() + " (" + columns + ") "
                + "VALUES "
                + "<foreach collection='model' item='item' separator=','>"
                + "(" + values + ")"
                + "</foreach>"
                + "</script>";
    }

    private String buildUpdate(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);
        Map<String, Object> where = asWhereMap(parameter);
        validateWhereKeys(where, metadata);

        return new SQL() {{
            UPDATE(metadata.getTableName());
            for (ColumnMetadata column : metadata.getUpdatableColumns()) {
                String value = column.resolveUpdateValue();
                SET(column.getColumnName() + " = " + (value != null ? value : "#{model." + column.getFieldName() + "}"));
            }
            if (where != null && !where.isEmpty()) {
                where.forEach((key, value) -> {
                    ColumnMetadata column = metadata.getColumn(key);
                    if (column != null) {
                        WHERE(column.getColumnName() + " = #{where." + key + "}");
                    }
                });
            } else {
                for (ColumnMetadata pkColumn : metadata.getPrimaryKeyColumns()) {
                    WHERE(pkColumn.getColumnName() + " = #{model." + pkColumn.getFieldName() + "}");
                }
            }
        }}.toString();
    }

    private String buildSelectiveUpdate(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);
        Map<String, Object> where = asWhereMap(parameter);
        validateWhereKeys(where, metadata);

        StringBuilder sb = new StringBuilder("<script>\nUPDATE ");
        sb.append(metadata.getTableName());
        sb.append("\n<set>\n");

        for (ColumnMetadata column : metadata.getUpdatableColumns()) {
            String updateValue = column.resolveUpdateValue();
            if (updateValue != null) {
                // @XDefaultValue updateValue → 항상 포함
                sb.append(column.getColumnName()).append(" = ").append(updateValue).append(",\n");
            } else {
                // null이 아닌 필드만 SET
                sb.append("<if test=\"model.").append(column.getFieldName()).append(" != null\">");
                sb.append(column.getColumnName()).append(" = #{model.").append(column.getFieldName()).append("},");
                sb.append("</if>\n");
            }
        }

        sb.append("</set>\n");

        // WHERE clause
        sb.append("WHERE ");
        if (where != null && !where.isEmpty()) {
            boolean first = true;
            for (String key : where.keySet()) {
                ColumnMetadata column = metadata.getColumn(key);
                if (column != null) {
                    if (!first) sb.append(" AND ");
                    sb.append(column.getColumnName()).append(" = #{where.").append(key).append("}");
                    first = false;
                }
            }
        } else {
            boolean first = true;
            for (ColumnMetadata pkColumn : metadata.getPrimaryKeyColumns()) {
                if (!first) sb.append(" AND ");
                sb.append(pkColumn.getColumnName()).append(" = #{model.").append(pkColumn.getFieldName()).append("}");
                first = false;
            }
        }

        sb.append("\n</script>");
        return sb.toString();
    }

    private String buildDelete(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);

        if (parameter.getWhere() instanceof Map) {
            Map<String, Object> where = asWhereMap(parameter);

            if (where == null || where.isEmpty()) {
                throw new IllegalArgumentException("DELETE with empty where-map is not allowed (would delete all rows)");
            }
            validateWhereKeys(where, metadata);

            return new SQL() {{
                DELETE_FROM(metadata.getTableName());
                where.forEach((key, value) -> {
                    ColumnMetadata column = metadata.getColumn(key);
                    WHERE(column.getColumnName() + " = #{where." + key + "}");
                });
            }}.toString();
        } else {
            boolean compositeKey = metadata.getPrimaryKeyColumns().size() > 1;
            return new SQL() {{
                DELETE_FROM(metadata.getTableName());
                for (ColumnMetadata pkColumn : metadata.getPrimaryKeyColumns()) {
                    if (compositeKey) {
                        WHERE(pkColumn.getColumnName() + " = #{where." + pkColumn.getFieldName() + "}");
                    } else {
                        WHERE(pkColumn.getColumnName() + " = #{where}");
                    }
                }
            }}.toString();
        }
    }

    private String buildFindById(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);
        boolean compositeKey = metadata.getPrimaryKeyColumns().size() > 1;
        return new SQL() {{
            SELECT(selectAllColumns(metadata));
            FROM(metadata.getTableName());
            for (ColumnMetadata pkColumn : metadata.getPrimaryKeyColumns()) {
                if (compositeKey) {
                    WHERE(pkColumn.getColumnName() + " = #{where." + pkColumn.getFieldName() + "}");
                } else {
                    WHERE(pkColumn.getColumnName() + " = #{where}");
                }
            }
        }}.toString();
    }

    private String buildFindAll(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);
        Map<String, Object> where = asWhereMap(parameter);
        validateWhereKeys(where, metadata);

        return new SQL() {{
            SELECT(selectAllColumns(metadata));
            FROM(metadata.getTableName());
            if (where != null && !where.isEmpty()) {
                where.forEach((key, value) -> {
                    ColumnMetadata column = metadata.getColumn(key);
                    if (column != null) {
                        WHERE(column.getColumnName() + " = #{where." + key + "}");
                    }
                });
            }
        }}.toString();
    }

    private String buildFindWhere(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);
        Map<String, Object> where = asWhereMap(parameter);

        if (where == null || where.isEmpty()) {
            throw new IllegalArgumentException("findWhere requires non-empty where conditions. Use findAll() for unfiltered queries.");
        }
        validateWhereKeys(where, metadata);

        return new SQL() {{
            SELECT(selectAllColumns(metadata));
            FROM(metadata.getTableName());
            if (!where.isEmpty()) {
                where.forEach((key, value) -> {
                    ColumnMetadata column = metadata.getColumn(key);
                    if (column != null) {
                        WHERE(column.getColumnName() + " = #{where." + key + "}");
                    }
                });
            }
        }}.toString();
    }

    private String buildCount(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);
        Map<String, Object> where = asWhereMap(parameter);
        validateWhereKeys(where, metadata);

        return new SQL() {{
            SELECT("COUNT(*)");
            FROM(metadata.getTableName());
            if (where != null && !where.isEmpty()) {
                where.forEach((key, value) -> {
                    ColumnMetadata column = metadata.getColumn(key);
                    if (column != null) {
                        WHERE(column.getColumnName() + " = #{where." + key + "}");
                    }
                });
            }
        }}.toString();
    }

    private String buildFindOneBy(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);
        Map<String, Object> where = asWhereMap(parameter);
        validateWhereKeys(where, metadata);

        return new SQL() {{
            SELECT(selectAllColumns(metadata));
            FROM(metadata.getTableName());
            if (where != null && !where.isEmpty()) {
                where.forEach((key, value) -> {
                    ColumnMetadata column = metadata.getColumn(key);
                    if (column != null) {
                        WHERE(column.getColumnName() + " = #{where." + key + "}");
                    }
                });
            }
            LIMIT(1);
        }}.toString();
    }

    /**
     * INSERT ... ON DUPLICATE KEY UPDATE
     * PK 충돌 시 UPDATE로 전환되므로 별도 exists 체크 없이 원자적으로 동작.
     */
    private String buildUpsert(XMapperParameter parameter) {
        EntityMetadata metadata = getMetadata(parameter);

        List<ColumnMetadata> insertColumns = metadata.getInsertableColumns().stream()
                .filter(c -> !c.isAutoIncrement())
                .filter(c -> !(c.isDBDefaultUsed() && c.resolveInsertValue() == null))
                .toList();

        String columns = insertColumns.stream()
                .map(ColumnMetadata::getColumnName)
                .collect(Collectors.joining(", "));

        String values = insertColumns.stream()
                .map(c -> {
                    String v = c.resolveInsertValue();
                    return v != null ? v : "#{model." + c.getFieldName() + "}";
                })
                .collect(Collectors.joining(", "));

        String updateSet = metadata.getUpdatableColumns().stream()
                .map(c -> {
                    String v = c.resolveUpdateValue();
                    return c.getColumnName() + " = " + (v != null ? v : "#{model." + c.getFieldName() + "}");
                })
                .collect(Collectors.joining(", "));

        return "INSERT INTO " + metadata.getTableName()
                + " (" + columns + ") VALUES (" + values + ")"
                + " ON DUPLICATE KEY UPDATE " + updateSet;
    }

    // ──────────────────────────────────────────
    // Cache key & helpers
    // ──────────────────────────────────────────

    /**
     * Cache key format: "ClassName:operation[:whereField1,whereField2,...]"
     *
     * The where field names determine the SQL template structure.
     * Actual values don't affect the template (MyBatis uses #{} placeholders),
     * so only the key set is included.
     *
     * ORDER BY is excluded from the cache key since it's appended dynamically.
     */
    private String cacheKey(XMapperParameter parameter, String operation) {
        StringBuilder key = new StringBuilder(parameter.getResultClass().getName())
                .append(':').append(operation);

        if (parameter.getWhere() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> whereMap = (Map<String, Object>) parameter.getWhere();
            if (!whereMap.isEmpty()) {
                key.append(':');
                key.append(whereMap.keySet().stream().sorted().collect(Collectors.joining(",")));
            }
        }

        return key.toString();
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> asWhereMap(XMapperParameter parameter) {
        return (parameter.getWhere() instanceof Map) ? (Map<String, Object>) parameter.getWhere() : null;
    }

    private String selectAllColumns(EntityMetadata metadata) {
        return metadata.getColumns().values().stream()
                .map(ColumnMetadata::getColumnName)
                .collect(Collectors.joining(", "));
    }

    private void validateWhereKeys(Map<String, Object> where, EntityMetadata metadata) {
        if (where == null || where.isEmpty()) return;
        for (String key : where.keySet()) {
            if (metadata.getColumn(key) == null) {
                throw new IllegalArgumentException(
                        "Unknown column key '" + key + "' for entity " + metadata.getTableName()
                        + ". Valid keys: " + metadata.getColumns().keySet());
            }
        }
    }

    private EntityMetadata getMetadata(XMapperParameter parameter) {
        Class<?> modelClass = parameter.getResultClass();
        return METADATA_FACTORY.getMetadata(modelClass, null);
    }
}