package one.axim.framework.mybatis.meta;

import one.axim.framework.mybatis.exception.XBuilderInvalidModelException;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class EntityMetadata {

    private final String tableName;
    private final Class<?> keyClass;
    private final Class<?> modelClass;
    private final Map<String, ColumnMetadata> columns;
    private final List<ColumnMetadata> primaryKeyColumns;
    private final List<ColumnMetadata> insertableColumns;
    private final List<ColumnMetadata> updatableColumns;

    public EntityMetadata(Class<?> modelClass, Class<?> keyClass, String tableName, Map<String, ColumnMetadata> columns) {
        this.modelClass = modelClass;
        this.keyClass = keyClass;
        this.tableName = tableName;
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

    public ColumnMetadata getColumn(String fieldName) {
        return columns.get(fieldName);
    }
}
