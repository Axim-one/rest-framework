---
name: axim-rest-framework
description: Build Spring Boot REST APIs with Axim REST Framework. Use when creating entities, repositories, services, controllers, error handling, or pagination with the axim-rest-framework (Spring Boot + MyBatis). Covers @XEntity, @XRepository, IXRepository, query derivation, save/modify/upsert, XPagination, XPage, error codes, i18n exceptions, and declarative REST client.
---

# Axim REST Framework

Spring Boot + MyBatis lightweight REST framework. Annotation-based entity mapping and repository proxy pattern that minimizes boilerplate while keeping MyBatis SQL control.

**Version:** 1.1.0
**Requirements:** Java 17+, Spring Boot 3.3+, MySQL 5.7+/8.0+, MyBatis 3.0+
**Repository:** https://github.com/Axim-one/rest-framework

## Installation

### Gradle

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Axim-one.rest-framework:core:1.1.0'
    implementation 'com.github.Axim-one.rest-framework:rest-api:1.1.0'
    implementation 'com.github.Axim-one.rest-framework:mybatis:1.1.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>core</artifactId>
        <version>1.1.0</version>
    </dependency>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>rest-api</artifactId>
        <version>1.1.0</version>
    </dependency>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>mybatis</artifactId>
        <version>1.1.0</version>
    </dependency>
</dependencies>
```

## Application Setup

CRITICAL: All four annotations are required on the main application class.

```java
@ComponentScan({"one.axim.framework.rest", "one.axim.framework.mybatis", "com.myapp"})
@SpringBootApplication
@XRepositoryScan("com.myapp")
@MapperScan({"one.axim.framework.mybatis.mapper", "com.myapp"})
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### application.properties

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
mybatis.config-location=classpath:mybatis-config.xml

# Optional: HTTP Client
axim.rest.client.pool-size=200
axim.rest.client.connection-request-timeout=30
axim.rest.client.response-timeout=30

# Optional: Session/Token
axim.rest.session.secret-key=your-hmac-secret-key
axim.rest.session.expire-days=7

# Optional: i18n
axim.rest.message.default-language=ko-KR
axim.rest.message.language-header=Accept-Language
```

### mybatis-config.xml

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
    <settings>
        <setting name="cacheEnabled" value="true"/>
        <setting name="useGeneratedKeys" value="true"/>
        <setting name="defaultExecutorType" value="SIMPLE"/>
        <setting name="defaultStatementTimeout" value="10"/>
        <setting name="callSettersOnNulls" value="true"/>
        <setting name="mapUnderscoreToCamelCase" value="true"/>
    </settings>
    <objectFactory type="one.axim.framework.mybatis.plugin.XObjectFactory"/>
    <plugins>
        <plugin interceptor="one.axim.framework.mybatis.plugin.XResultInterceptor"/>
    </plugins>
    <mappers>
        <mapper class="one.axim.framework.mybatis.mapper.CommonMapper"/>
    </mappers>
</configuration>
```

## Entity Definition

Use `@XEntity` to map a class to a database table. Fields auto-map using camelCase → snake_case conversion.

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

### @XDefaultValue Patterns

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

## Annotations Reference

| Annotation | Target | Description |
|---|---|---|
| `@XEntity(value, schema)` | Class | Maps class to database table |
| `@XColumn(value, isPrimaryKey, isAutoIncrement, insert, update)` | Field | Column mapping with options |
| `@XDefaultValue(value, updateValue, isDBDefaultUsed, isDBValue)` | Field | Default values for INSERT/UPDATE |
| `@XIgnoreColumn` | Field | Excludes field from DB mapping |
| `@XRepository` | Interface | Marks repository for proxy generation |
| `@XRepositoryScan(basePackages)` | Class | Scans for @XRepository interfaces |

## Repository

Extend `IXRepository<K, T>` and annotate with `@XRepository`:

```java
@XRepository
public interface UserRepository extends IXRepository<Long, User> {
    User findByEmail(String email);
    List<User> findByStatus(String status);
    boolean existsByEmail(String email);
    long countByStatus(String status);
    int deleteByStatusAndName(String status, String name);
}
```

### Repository API

| Method | Return | Description |
|---|---|---|
| `save(entity)` | `K` | PK null → INSERT, PK present → Upsert (Composite: all PKs set → upsert) |
| `insert(entity)` | `K` | Plain INSERT with auto-generated ID (Composite: returns key class) |
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

### CRUD Examples

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
repository.save(orderItem);    // All PKs set → upsert, any null → insert
repository.insert(orderItem);  // Returns OrderItemKey with both PK values
```

## Query Derivation

Declare methods and SQL is auto-generated from the method name.

**Supported Prefixes:** `findBy`, `findAllBy`, `countBy`, `existsBy`, `deleteBy`
**Condition Combinator:** `And`

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

## Pagination

IMPORTANT: Always use `XPagination` and `XPage` for pagination. NEVER create custom pagination classes (e.g., PageRequest, PageResponse, PaginationDTO). The framework handles COUNT, ORDER BY, and LIMIT automatically.

```java
XPagination pagination = new XPagination();
pagination.setPage(1);       // 1-based
pagination.setSize(20);
pagination.addOrder(new XOrder("createdAt", XDirection.DESC));

XPage<User> result = userRepository.findAll(pagination);
result.getTotalCount();   // total rows
result.getPage();         // current page
result.getPageRows();     // rows in this page
result.getHasNext();      // more pages?

// Controller with auto-binding
@GetMapping
public XPage<User> searchUsers(@XPaginationDefault XPagination pagination) {
    return userRepository.findAll(pagination);
}
// Accepts: ?page=1&size=10&sort=email,asc
```

## Query Strategy: Repository vs Custom Mapper

The framework provides two query approaches. Choosing the right one is critical:

### Use @XRepository (auto-generated SQL) when:
- Exact-match WHERE conditions: `findByStatus("ACTIVE")`
- Single-table CRUD operations
- Simple AND conditions: `findByUserIdAndStatus(id, status)`

### Use @Mapper (custom SQL) when:
- **LIKE / partial match**: `WHERE name LIKE '%keyword%'`
- **BETWEEN / range**: `WHERE created_at BETWEEN ? AND ?`
- **JOIN**: Any query involving multiple tables
- **Subqueries**: `WHERE id IN (SELECT ...)`
- **Aggregation**: `GROUP BY`, `HAVING`, `SUM()`, `COUNT()` per group
- **OR conditions**: `WHERE status = ? OR role = ?`
- **Complex sorting**: Sorting by computed/joined columns
- **UNION**: Combining result sets

**CRITICAL: Query derivation only supports exact-match `=` with `And` combinator. It does NOT support LIKE, BETWEEN, OR, IN, >, <, JOIN, or any other SQL operator. When these are needed, immediately create a @Mapper interface — do not attempt to work around Repository limitations.**

### Custom Mapper with Pagination (XPagination)

Custom @Mapper methods integrate with XPagination seamlessly. The framework's `XResultInterceptor` automatically intercepts the query to handle COUNT, ORDER BY, and LIMIT — you only write the base SELECT.

**Rules for custom mapper pagination:**
1. Add `XPagination` as the **first parameter**
2. Add `Class<?>` as the **last parameter** (pass the entity class — used for result type mapping)
3. Return `XPage<T>` as the return type
4. Write **only the base SELECT** — do NOT add ORDER BY or LIMIT in your SQL

```java
@Mapper
public interface UserMapper {

    // LIKE search with pagination
    @Select("SELECT * FROM users WHERE name LIKE CONCAT('%', #{keyword}, '%')")
    XPage<User> searchByName(XPagination pagination, @Param("keyword") String keyword, Class<?> cls);

    // BETWEEN with pagination
    @Select("SELECT * FROM users WHERE created_at BETWEEN #{from} AND #{to}")
    XPage<User> findByDateRange(XPagination pagination,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to, Class<?> cls);

    // JOIN with pagination
    @Select("SELECT u.*, d.name AS department_name FROM users u " +
            "INNER JOIN departments d ON u.department_id = d.id " +
            "WHERE d.status = #{status}")
    XPage<UserWithDepartment> findUsersWithDepartment(XPagination pagination,
                                                      @Param("status") String status, Class<?> cls);

    // Multiple conditions (OR, IN)
    @Select("<script>" +
            "SELECT * FROM users WHERE status IN " +
            "<foreach item='s' collection='statuses' open='(' separator=',' close=')'>" +
            "#{s}" +
            "</foreach>" +
            "</script>")
    XPage<User> findByStatuses(XPagination pagination,
                               @Param("statuses") List<String> statuses, Class<?> cls);

    // Without pagination — just return List<T> (no XPagination, no Class<?>)
    @Select("SELECT * FROM users WHERE email LIKE CONCAT('%', #{keyword}, '%')")
    List<User> searchByEmail(@Param("keyword") String keyword);

    // Aggregation (non-paginated, returns custom projection)
    @Select("SELECT department_id, COUNT(*) as user_count FROM users GROUP BY department_id")
    List<Map<String, Object>> countByDepartment();
}
```

### Controller Pattern: Repository + Mapper Together

A typical controller uses Repository for simple operations and Mapper for complex queries:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;  // simple CRUD
    private final UserMapper userMapper;           // complex queries

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userRepository.findOne(id);
    }

    @GetMapping
    public XPage<User> searchUsers(@XPaginationDefault XPagination pagination,
                                   @RequestParam(required = false) String keyword) {
        if (keyword != null) {
            return userMapper.searchByName(pagination, keyword, User.class);
        }
        return userRepository.findAll(pagination);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        userRepository.save(user);
        return user;
    }
}
```

## Error Code System

### ErrorCode Record

```java
public record ErrorCode(String code, String messageKey) {}
```

### Built-in Exceptions

| Code | Exception | HTTP | Description |
|---|---|---|---|
| `1` | `UnAuthorizedException` | 401 | Authentication required |
| `2` | `UnAuthorizedException` | 401 | Invalid credentials |
| `3` | `UnAuthorizedException` | 401 | Token expired |
| `11` | `InvalidRequestParameterException` | 400 | Invalid parameter |
| `12` | `InvalidRequestParameterException` | 400 | Request body not found |
| `13` | `InvalidRequestParameterException` | 400 | Method not supported |
| `100` | `NotFoundException` | 404 | Not found |
| `900` | `UnavailableServerException` | 504 | Server unavailable |
| `999` | `UnknownServerException` | 500 | Unknown error |

### Custom Exception

```java
public class UserException extends XRestException {
    public static final ErrorCode DUPLICATE_EMAIL = new ErrorCode("2001", "user.error.duplicate-email");
    public static final ErrorCode INACTIVE_ACCOUNT = new ErrorCode("2002", "user.error.inactive-account");

    public UserException(ErrorCode error) {
        super(HttpStatus.BAD_REQUEST, error);
    }

    public UserException(ErrorCode error, String description) {
        super(HttpStatus.BAD_REQUEST, error, description);
    }
}

// Usage
throw new UserException(UserException.DUPLICATE_EMAIL, "alice@example.com already exists");
```

### i18n Messages

```properties
# messages.properties
user.error.duplicate-email=Email already exists.

# messages_ko.properties
user.error.duplicate-email=이미 존재하는 이메일입니다.
```

### Error Response Format

```json
{
    "code": "2001",
    "message": "Email already exists.",
    "description": "alice@example.com already exists",
    "data": null
}
```

## Declarative REST Client

```java
@XRestService(name = "user-service", host = "${external.user-service.host}", version = "v1")
public interface UserServiceClient {

    @XRestAPI(url = "/users/{id}", method = XHttpMethod.GET)
    User getUser(@PathVariable("id") Long id);

    @XRestAPI(url = "/users", method = XHttpMethod.POST)
    User createUser(@RequestBody User user);
}
```

## Complete Service Layer Example

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User create(User user) {
        userRepository.save(user);
        return user;
    }

    public User partialUpdate(Long id, UserUpdateRequest req) {
        User user = new User();
        user.setId(id);
        user.setName(req.getName());
        userRepository.modify(user);    // selective UPDATE
        return userRepository.findOne(id);
    }

    public XPage<User> list(int page, int size) {
        XPagination pagination = new XPagination();
        pagination.setPage(page);
        pagination.setSize(size);
        pagination.addOrder(new XOrder("createdAt", XDirection.DESC));
        return userRepository.findAll(pagination);
    }
}
```

## Architecture

```
Application Code                    Framework Internals
────────────────                    ───────────────────
@XRepository                        XRepositoryBeanScanner
UserRepository                           ↓
  extends IXRepository<K, T>        XRepositoryProxyFactoryBean
       ↓                                 ↓
  (JDK Dynamic Proxy)              XRepositoryProxy (InvocationHandler)
       ↓                                 ↓
                                    CommonMapper (@Mapper)
                                         ↓
                                    CrudSqlProvider (SQL Generation + Cache)
                                         ↓
                                    XResultInterceptor (Pagination, Result Mapping)
```
