package one.axim.framework.mybatis.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Excludes a field from database column mapping entirely.
 *
 * <p>Fields annotated with {@code @XIgnoreColumn} are skipped during entity metadata
 * discovery, so they are not included in any generated SQL (SELECT, INSERT, UPDATE).</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @Data
 * @XEntity("users")
 * public class User {
 *     @XColumn(isPrimaryKey = true, isAutoIncrement = true)
 *     private Long id;
 *     private String name;
 *
 *     @XIgnoreColumn
 *     private String displayLabel;  // not mapped to any DB column
 * }
 * }</pre>
 *
 * @see XEntity
 * @see XColumn
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface XIgnoreColumn {

}
