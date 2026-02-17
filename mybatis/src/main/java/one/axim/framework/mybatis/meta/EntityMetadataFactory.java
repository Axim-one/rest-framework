package one.axim.framework.mybatis.meta;

import one.axim.framework.core.utils.NamingConvert;
import one.axim.framework.mybatis.annotation.XColumn;
import one.axim.framework.mybatis.annotation.XDefaultValue;
import one.axim.framework.mybatis.annotation.XEntity;
import one.axim.framework.mybatis.annotation.XIgnoreColumn;
import one.axim.framework.mybatis.exception.XBuilderInvalidModelException;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class EntityMetadataFactory {

    private record CacheKey(Class<?> modelClass, Class<?> keyClass) {}

    private static final Map<CacheKey, EntityMetadata> entityMetadataCache = new ConcurrentHashMap<>();

    public EntityMetadata getMetadata(Class<?> modelClass, Class<?> keyClass) {
        CacheKey cacheKey = new CacheKey(modelClass, keyClass);
        return entityMetadataCache.computeIfAbsent(cacheKey, k -> parse(k.modelClass(), k.keyClass()));
    }

    private EntityMetadata parse(Class<?> modelClass, Class<?> keyClass) {
        XEntity entity = modelClass.getAnnotation(XEntity.class);
        if (entity == null) {
            throw new XBuilderInvalidModelException("Not found @XEntity Annotation in Model Class: " + modelClass.getName());
        }

        String tableName;
        if (StringUtils.hasText(entity.schema())) {
            tableName = "`" + entity.schema() + "`.`" + entity.value() + "`";
        } else {
            tableName = "`" + entity.value() + "`";
        }

        Map<String, ColumnMetadata> columnMetadataMap = new LinkedHashMap<>();
        for (Field field : getAllFields(modelClass)) {
            if (field.isAnnotationPresent(XIgnoreColumn.class)) {
                continue;
            }

            PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(modelClass, field.getName());
            if (pd == null || pd.getReadMethod() == null) {
                continue;
            }

            String fieldName = field.getName();
            String columnName;
            boolean isPrimaryKey = false;
            boolean isAutoIncrement = false;
            boolean isInsertable = true;
            boolean isUpdatable = true;

            XColumn columnAnnotation = field.getAnnotation(XColumn.class);
            if (columnAnnotation != null) {
                columnName = StringUtils.hasText(columnAnnotation.value()) ? columnAnnotation.value() : NamingConvert.toUnderScoreName(fieldName);
                isPrimaryKey = columnAnnotation.isPrimaryKey();
                isAutoIncrement = columnAnnotation.isAutoIncrement();
                isInsertable = columnAnnotation.insert();
                isUpdatable = columnAnnotation.update();
            } else {
                columnName = NamingConvert.toUnderScoreName(fieldName);
            }

            // @XDefaultValue parsing
            String defaultValue = "";
            String defaultUpdateValue = "";
            boolean isDBDefaultUsed = false;
            boolean isDBValue = false;

            XDefaultValue defaultValueAnnotation = field.getAnnotation(XDefaultValue.class);
            if (defaultValueAnnotation != null) {
                defaultValue = defaultValueAnnotation.value();
                defaultUpdateValue = defaultValueAnnotation.updateValue();
                isDBDefaultUsed = defaultValueAnnotation.isDBDefaultUsed();
                isDBValue = defaultValueAnnotation.isDBValue();
            }

            columnMetadataMap.put(fieldName, ColumnMetadata.builder()
                    .fieldName(fieldName)
                    .columnName(columnName)
                    .isPrimaryKey(isPrimaryKey)
                    .isAutoIncrement(isAutoIncrement)
                    .isInsertable(isInsertable)
                    .isUpdatable(isUpdatable)
                    .propertyDescriptor(pd)
                    .field(field)
                    .defaultValue(defaultValue)
                    .defaultUpdateValue(defaultUpdateValue)
                    .isDBDefaultUsed(isDBDefaultUsed)
                    .isDBValue(isDBValue)
                    .build());
        }

        if (columnMetadataMap.isEmpty()) {
            throw new XBuilderInvalidModelException("No mappable columns found in model: " + modelClass.getName());
        }

        return new EntityMetadata(modelClass, keyClass, tableName, columnMetadataMap);
    }

    /**
     * Collects all fields from the class hierarchy (child → parent order).
     * Stops at Object.class. Child fields take precedence over parent fields with the same name.
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) continue;
                if (!seen.containsKey(field.getName())) {
                    seen.put(field.getName(), true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }
}
