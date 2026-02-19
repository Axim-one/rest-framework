package one.axim.framework.mybatis.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies default values for INSERT and UPDATE operations on entity fields.
 *
 * <p>Supports four common patterns for default value handling:</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Pattern 1: Use DB DEFAULT (column omitted from INSERT)
 * @XDefaultValue(isDBDefaultUsed = true)
 * private String region;
 *
 * // Pattern 2: Literal string value on INSERT
 * @XDefaultValue(value = "ACTIVE", isDBDefaultUsed = false)
 * private String status;
 * // → INSERT: VALUES(..., 'ACTIVE', ...)
 *
 * // Pattern 3: DB expression on INSERT
 * @XDefaultValue(value = "NOW()", isDBValue = true, isDBDefaultUsed = false)
 * private LocalDateTime createdAt;
 * // → INSERT: VALUES(..., NOW(), ...)
 *
 * // Pattern 4: Auto-set value on UPDATE
 * @XDefaultValue(updateValue = "NOW()", isDBValue = true)
 * private LocalDateTime updatedAt;
 * // → UPDATE: SET updated_at = NOW()
 * }</pre>
 *
 * @see XColumn
 * @see XEntity
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface XDefaultValue {

    /**
     * Default value to use on INSERT. Treated as a literal string unless
     * {@link #isDBValue()} is {@code true}, in which case it is embedded as a raw SQL expression.
     *
     * @return the default value for INSERT
     */
    String value() default "";

    /**
     * Whether to rely on the database's own DEFAULT for this column on INSERT.
     * When {@code true}, the column is omitted from the INSERT statement entirely.
     *
     * @return {@code true} to use the DB-defined default (default: {@code true})
     */
    boolean isDBDefaultUsed() default true;

    /**
     * Value to automatically set on UPDATE operations. Like {@link #value()}, it is treated
     * as a literal string unless {@link #isDBValue()} is {@code true}.
     *
     * @return the value to set on UPDATE
     */
    String updateValue() default "";

    /**
     * Whether {@link #value()} and {@link #updateValue()} should be treated as raw SQL
     * expressions (e.g., {@code NOW()}, {@code UUID()}) rather than quoted string literals.
     *
     * @return {@code true} if the value is a DB expression
     */
    boolean isDBValue() default false;
}
