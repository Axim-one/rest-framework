package one.axim.framework.mybatis.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Maps an entity field to a database column with fine-grained control over column behavior.
 *
 * <p>If this annotation is not present on a field, the framework automatically maps it
 * using camelCase-to-snake_case name conversion with default settings.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Primary key with auto-increment
 * @XColumn(isPrimaryKey = true, isAutoIncrement = true)
 * private Long id;
 *
 * // Custom column name mapping
 * @XColumn("user_email_address")
 * private String email;
 *
 * // Read-only column (excluded from INSERT and UPDATE)
 * @XColumn(insert = false, update = false)
 * private LocalDateTime createdAt;
 *
 * // Excluded from UPDATE only
 * @XColumn(update = false)
 * private String createdBy;
 * }</pre>
 *
 * @see XEntity
 * @see XDefaultValue
 * @see XIgnoreColumn
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface XColumn {

    /**
     * Column name. If empty, the field name is converted from camelCase to snake_case.
     *
     * @return the column name
     */
    String value() default "";

    /**
     * Whether this column is the primary key.
     *
     * @return {@code true} if this column is the primary key
     */
    boolean isPrimaryKey() default false;

    /**
     * Whether this column is included in UPDATE statements.
     *
     * @return {@code true} if this column can be updated (default: {@code true})
     */
    boolean update() default true;

    /**
     * Whether this column is included in INSERT statements.
     *
     * @return {@code true} if this column can be inserted (default: {@code true})
     */
    boolean insert() default true;

    /**
     * Whether this column uses database auto-increment for key generation.
     *
     * @return {@code true} if the column is auto-incremented
     */
    boolean isAutoIncrement() default false;
}
