package one.axim.framework.mybatis.meta;

import lombok.Builder;
import lombok.Getter;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;

@Getter
@Builder
public class ColumnMetadata {
    private final String fieldName;
    private final String columnName;
    private final boolean isPrimaryKey;
    private final boolean isAutoIncrement;
    private final boolean isInsertable;
    private final boolean isUpdatable;
    private final PropertyDescriptor propertyDescriptor;
    private final Field field;

    // @XDefaultValue support
    private final String defaultValue;       // INSERT 시 사용할 기본값 (empty = 없음)
    private final String defaultUpdateValue; // UPDATE 시 사용할 기본값 (empty = 없음)
    private final boolean isDBDefaultUsed;   // true → INSERT 에서 컬럼 생략 (DB DEFAULT 사용)
    private final boolean isDBValue;         // true → 값이 DB 표현식 (NOW() 등), false → 문자열 리터럴

    /**
     * INSERT 시 #{model.field} 대신 사용할 값을 반환.
     * null 이면 일반 파라미터 바인딩 사용.
     */
    public String resolveInsertValue() {
        if (defaultValue == null || defaultValue.isEmpty()) return null;
        return isDBValue ? defaultValue : "'" + defaultValue.replace("'", "''") + "'";
    }

    /**
     * UPDATE 시 #{model.field} 대신 사용할 값을 반환.
     * null 이면 일반 파라미터 바인딩 사용.
     */
    public String resolveUpdateValue() {
        if (defaultUpdateValue == null || defaultUpdateValue.isEmpty()) return null;
        return isDBValue ? defaultUpdateValue : "'" + defaultUpdateValue.replace("'", "''") + "'";
    }
}