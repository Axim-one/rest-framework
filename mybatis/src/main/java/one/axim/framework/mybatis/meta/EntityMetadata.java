package one.axim.framework.mybatis.meta;

import one.axim.framework.mybatis.dialect.XDialectHolder;
import one.axim.framework.mybatis.exception.XBuilderInvalidModelException;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class EntityMetadata {

    /** 고정 테이블명 (구 생성자로 만든 경우). 방언 대응 경로에서는 null. */
    private final String tableName;
    /** 따옴표 없는 스키마명 (없으면 null/빈 문자열). v1.5.0 */
    private final String schema;
    /** 따옴표 없는 테이블명. v1.5.0 */
    private final String rawTableName;
    private final Class<?> keyClass;
    private final Class<?> modelClass;
    private final Map<String, ColumnMetadata> columns;
    private final List<ColumnMetadata> primaryKeyColumns;
    private final List<ColumnMetadata> insertableColumns;
    private final List<ColumnMetadata> updatableColumns;

    /**
     * 이미 따옴표가 붙은 테이블명을 그대로 쓴다 (1.4.x 호환). 방언과 무관하게 고정된다.
     */
    public EntityMetadata(Class<?> modelClass, Class<?> keyClass, String tableName, Map<String, ColumnMetadata> columns) {
        this(modelClass, keyClass, tableName, null, null, columns);
    }

    /**
     * 따옴표 없는 스키마/테이블명을 받는다. 따옴표는 {@link #getTableName()} 호출 시점의
     * 방언으로 붙는다 — 메타데이터 캐시가 방언 결정보다 먼저 만들어져도 맞게 나간다. (v1.5.0)
     */
    public EntityMetadata(Class<?> modelClass, Class<?> keyClass, String schema, String rawTableName,
                          Map<String, ColumnMetadata> columns) {
        this(modelClass, keyClass, null, schema, rawTableName, columns);
    }

    private EntityMetadata(Class<?> modelClass, Class<?> keyClass, String tableName, String schema,
                           String rawTableName, Map<String, ColumnMetadata> columns) {
        this.modelClass = modelClass;
        this.keyClass = keyClass;
        this.tableName = tableName;
        this.schema = schema;
        this.rawTableName = rawTableName;
        this.columns = columns;
        this.primaryKeyColumns = columns.values().stream()
                .filter(ColumnMetadata::isPrimaryKey)
                .toList();
        this.insertableColumns = columns.values().stream()
                .filter(ColumnMetadata::isInsertable)
                .toList();
        this.updatableColumns = columns.values().stream()
                .filter(ColumnMetadata::isUpdatable)
                .toList();

        if (this.primaryKeyColumns.isEmpty()) {
            throw new XBuilderInvalidModelException("Entity " + modelClass.getName() + " must have at least one @XColumn(isPrimaryKey = true)");
        }
    }

    /**
     * SQL 에 쓰는 테이블명. MySQL {@code `schema`.`table`}, PostgreSQL {@code "schema"."table"}.
     */
    public String getTableName() {
        if (tableName != null) {
            return tableName;
        }
        return XDialectHolder.current().qualifiedTableName(schema, rawTableName);
    }

    public ColumnMetadata getColumn(String fieldName) {
        return columns.get(fieldName);
    }
}
