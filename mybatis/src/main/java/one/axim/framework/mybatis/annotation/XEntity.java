package one.axim.framework.mybatis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps an entity class to a database table.
 *
 * <p>When applied to a class, the framework uses reflection to discover all fields
 * (including inherited ones) and maps them to table columns via camelCase-to-snake_case
 * conversion. Fields annotated with {@link XIgnoreColumn} are excluded.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @Data
 * @XEntity("users")
 * public class User {
 *     @XColumn(isPrimaryKey = true, isAutoIncrement = true)
 *     private Long id;
 *     private String email;
 *     private String name;
 * }
 *
 * // With explicit schema
 * @XEntity(value = "orders", schema = "shop")
 * public class Order {
 *     @XColumn(isPrimaryKey = true, isAutoIncrement = true)
 *     private Long id;
 *     private String productName;
 * }
 * }</pre>
 *
 * @see XColumn
 * @see XIgnoreColumn
 * @see XRepository
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface XEntity {

    /**
     * Table name. If empty, the class name is used with camelCase-to-snake_case conversion.
     *
     * @return the table name
     */
    String value() default "";

    /**
     * Database schema name (optional). When specified, the generated SQL uses {@code schema.table} format.
     *
     * @return the schema name
     */
    String schema() default "";
}
