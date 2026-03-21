# Entity & Repository Reference

## Entity Definition

Map a class to a database table with `@XEntity`. Fields auto-map via camelCase to snake_case.

```java
@Data
@XEntity("users")
public class User {

    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;

    private String email;
    private String name;

    @XDefaultValue(value = "NOW()", isDBValue = true)
    private LocalDateTime createdAt;

    @XDefaultValue(updateValue = "NOW()", isDBValue = true)
    private LocalDateTime updatedAt;

    @XColumn(insert = false, update = false)
    private String readOnlyField;

    @XIgnoreColumn
    private String transientField;
}
```

### Entity with Schema

```java
@XEntity(value = "orders", schema = "shop")
public class Order { ... }
```

### Entity Inheritance

Parent class fields are automatically included:

```java
public class BaseEntity {
    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;

    @XDefaultValue(value = "NOW()", isDBValue = true)
    private LocalDateTime createdAt;

    @XDefaultValue(updateValue = "NOW()", isDBValue = true)
    private LocalDateTime updatedAt;
}

@Data
@XEntity("partners")
public class Partner extends BaseEntity {
    private String name;
    private String status;
}
```

## @XDefaultValue Patterns

```java
// Pattern 1: Use DB DEFAULT (column omitted from INSERT)
@XDefaultValue(isDBDefaultUsed = true)
private String region;

// Pattern 2: Literal string value on INSERT
@XDefaultValue(value = "ACTIVE", isDBDefaultUsed = false)
private String status;

// Pattern 3: DB expression on INSERT
@XDefaultValue(value = "NOW()", isDBValue = true, isDBDefaultUsed = false)
private LocalDateTime createdAt;

// Pattern 4: Auto-set value on UPDATE
@XDefaultValue(updateValue = "NOW()", isDBValue = true)
private LocalDateTime updatedAt;
```

**CRITICAL:** `isDBDefaultUsed` defaults to `true`. `@XDefaultValue(value = "ACTIVE")` silently ignores the value and uses DB DEFAULT. Must set `isDBDefaultUsed = false` for literal values.

```java
// WRONG — "ACTIVE" is IGNORED
@XDefaultValue(value = "ACTIVE")
private String status;

// CORRECT
@XDefaultValue(value = "ACTIVE", isDBDefaultUsed = false)
private String status;
```

## @XColumn Usage Rules

`@XColumn` is NOT required on every field. Only use when setting specific options.

```java
@Data
@XEntity("users")
public class User {

    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;           // needed — primary key

    private String email;      // auto-mapped to "email"
    private String userName;   // auto-mapped to "user_name"

    @XColumn("usr_email_addr")
    private String emailAddr;  // needed — custom column name

    @XColumn(insert = false, update = false)
    private String readOnly;   // needed — read-only field

    @XColumn(update = false)
    private String createdBy;  // needed — immutable after creation
}
```

| Situation | @XColumn | Example |
|---|---|---|
| Primary Key | **Required** | `@XColumn(isPrimaryKey = true, isAutoIncrement = true)` |
| Composite PK field | **Required** | `@XColumn(isPrimaryKey = true)` |
| Regular field (camelCase to snake_case) | **Omit** | `private String userName;` |
| Custom column name | **Required** | `@XColumn("usr_nm")` |
| Read-only / insert-only / update-only | **Required** | `@XColumn(insert = false, update = false)` |
| Exclude from DB entirely | Use `@XIgnoreColumn` | `@XIgnoreColumn private String temp;` |

## Annotations Reference

| Annotation | Target | Description |
|---|---|---|
| `@XEntity(value, schema)` | Class | Maps class to database table |
| `@XColumn(value, isPrimaryKey, isAutoIncrement, insert, update)` | Field | Column mapping with options |
| `@XDefaultValue(value, updateValue, isDBDefaultUsed, isDBValue)` | Field | Default values for INSERT/UPDATE |
| `@XIgnoreColumn` | Field | Excludes field from DB mapping |
| `@XRepository` | Interface | Marks repository for proxy generation |
| `@XRepositoryScan(basePackages)` | Class | Scans for @XRepository interfaces |

## Repository API

| Method | Return | Description |
|---|---|---|
| `save(entity)` | `K` | PK null -> INSERT, PK present -> Upsert |
| `insert(entity)` | `K` | Plain INSERT with auto-generated ID |
| `saveAll(List)` | `K` | Batch INSERT IGNORE |
| `update(entity)` | `int` | Full UPDATE (all columns including nulls) |
| `modify(entity)` | `int` | Selective UPDATE (non-null fields only) |
| `findOne(key)` | `T` | Find by primary key |
| `findAll()` | `List<T>` | Find all rows |
| `findAll(pagination)` | `XPage<T>` | Paginated find all |
| `findWhere(Map)` | `List<T>` | Find by conditions |
| `findWhere(pagination, Map)` | `XPage<T>` | Paginated find by conditions |
| `findOneWhere(Map)` | `T` | Find one by conditions |
| `exists(key)` | `boolean` | Check existence by PK |
| `count()` / `count(Map)` | `long` | Total / conditional count |
| `deleteById(key)` | `int` | Delete by primary key |
| `deleteWhere(Map)` | `int` | Delete by conditions |

## CRUD Examples

```java
// save() - Upsert
User user = new User();
user.setName("Alice");
userRepository.save(user);        // INSERT, auto-increment ID set on entity
user.setId(1L);
userRepository.save(user);        // INSERT ... ON DUPLICATE KEY UPDATE

// insert() - Plain INSERT
userRepository.insert(user);

// update() vs modify()
userRepository.update(user);      // SET name='Alice', email=NULL, status=NULL
userRepository.modify(user);      // SET name='Alice' (null fields skipped)

// saveAll() - Batch
userRepository.saveAll(List.of(user1, user2, user3));

// Find
User found = userRepository.findOne(1L);
List<User> active = userRepository.findWhere(Map.of("status", "ACTIVE"));
boolean exists = userRepository.exists(1L);
long count = userRepository.count(Map.of("status", "ACTIVE"));

// Delete
userRepository.deleteById(1L);
userRepository.deleteWhere(Map.of("status", "INACTIVE"));
```

## Composite Primary Key

Entities with multiple primary keys use a key class for `IXRepository<K, T>`.

```java
// Key class — field names must match entity PK field names
@Data
public class OrderItemKey {
    private Long orderId;
    private Long itemId;
}

// Entity — multiple @XColumn(isPrimaryKey = true)
@Data
@XEntity("order_items")
public class OrderItem {
    @XColumn(isPrimaryKey = true)
    private Long orderId;
    @XColumn(isPrimaryKey = true)
    private Long itemId;
    private int quantity;
    private BigDecimal price;
}

// Repository
@XRepository
public interface OrderItemRepository extends IXRepository<OrderItemKey, OrderItem> {}
```

```java
// Usage
OrderItemKey key = new OrderItemKey();
key.setOrderId(1L);
key.setItemId(100L);

repository.findOne(key);       // WHERE order_id = ? AND item_id = ?
repository.delete(key);        // WHERE order_id = ? AND item_id = ?
repository.save(orderItem);    // All PKs set -> upsert, any null -> insert
repository.insert(orderItem);  // Returns OrderItemKey with both PK values
```

## Query Derivation

Declare methods and SQL is auto-generated from the method name.

**Supported Prefixes:** `findBy`, `findAllBy`, `countBy`, `existsBy`, `deleteBy`
**Condition Combinator:** `And` only (exact-match `=` conditions)

```java
@XRepository
public interface OrderRepository extends IXRepository<Long, Order> {
    Order findByOrderNo(String orderNo);                           // WHERE order_no = ?
    List<Order> findByUserIdAndStatus(Long userId, String status); // WHERE user_id = ? AND status = ?
    long countByStatus(String status);                             // SELECT COUNT(*) WHERE status = ?
    boolean existsByOrderNo(String orderNo);                       // EXISTS check
    int deleteByUserIdAndStatus(Long userId, String status);       // DELETE WHERE ...
}
```

**Parsing rule:** `And` only splits when preceded by lowercase and followed by uppercase. `findByBrandName` is one field, `findByStatusAndName` is two fields.
