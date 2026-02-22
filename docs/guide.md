# Axim REST Framework - Usage Guide

A lightweight Spring Boot + MyBatis REST framework with annotation-based entity mapping, repository proxy pattern, query derivation, and pagination.

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

# Framework HTTP Client (optional)
axim.rest.client.pool-size=200
axim.rest.client.connection-request-timeout=30
axim.rest.client.response-timeout=30

# Framework Session (optional)
axim.rest.session.secret-key=your-hmac-secret-key
axim.rest.session.expire-days=7
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

Use `@XEntity` to map a class to a database table. Fields are automatically mapped using camelCase-to-snake_case conversion.

### Basic Entity

```java
@Data
@XEntity("users")
public class User {

    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;

    @XColumn("email")
    private String email;

    private String name;  // auto-mapped to snake_case column "name"

    @XDefaultValue(value = "NOW()", isDBValue = true)
    private LocalDateTime createdAt;

    @XDefaultValue(updateValue = "NOW()", isDBValue = true)
    private LocalDateTime updatedAt;

    @XColumn(insert = false, update = false)
    private String readOnlyField;

    @XIgnoreColumn
    private String transientField;  // excluded from all SQL
}
```

### Entity with Schema

```java
@XEntity(value = "orders", schema = "shop")
public class Order {
    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;
    private String productName;
    private Integer quantity;
}
```

### Entity Inheritance

Parent class fields are automatically included in the mapping.

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
// All fields mapped: id, createdAt, updatedAt, name, status
```

### Composite Primary Key Entity

For tables with composite primary keys, mark multiple fields with `@XColumn(isPrimaryKey = true)` and define a key class.

```java
// Key class — field names must match entity PK field names
@Data
public class OrderItemKey {
    private Long orderId;
    private Long itemId;
}

// Entity with composite primary key
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
```

## @XDefaultValue Patterns

Four common patterns for default value handling:

```java
// Pattern 1: Use DB DEFAULT (column omitted from INSERT)
@XDefaultValue(isDBDefaultUsed = true)
private String region;

// Pattern 2: Literal string value on INSERT
@XDefaultValue(value = "ACTIVE", isDBDefaultUsed = false)
private String status;
// INSERT: VALUES(..., 'ACTIVE', ...)

// Pattern 3: DB expression on INSERT
@XDefaultValue(value = "NOW()", isDBValue = true, isDBDefaultUsed = false)
private LocalDateTime createdAt;
// INSERT: VALUES(..., NOW(), ...)

// Pattern 4: Auto-set value on UPDATE
@XDefaultValue(updateValue = "NOW()", isDBValue = true)
private LocalDateTime updatedAt;
// UPDATE: SET updated_at = NOW()
```

## Repository Definition

Extend `IXRepository<K, T>` and annotate with `@XRepository` to get auto-generated CRUD operations.

```java
@XRepository
public interface UserRepository extends IXRepository<Long, User> {
    // All IXRepository methods are available automatically.
    // Add query derivation methods below:
    User findByEmail(String email);
    List<User> findByStatus(String status);
    boolean existsByEmail(String email);
    long countByStatus(String status);
    int deleteByStatusAndName(String status, String name);
}
```

## CRUD Operations

### save() - Upsert

```java
// When PK is null: plain INSERT (auto-generated ID is set on entity)
User user = new User();
user.setName("Alice");
userRepository.save(user);
// user.getId() now contains the generated ID

// When PK is present: INSERT ... ON DUPLICATE KEY UPDATE (atomic upsert)
user.setId(1L);
userRepository.save(user);
```

### insert() - Plain INSERT

```java
User user = new User();
user.setName("Bob");
userRepository.insert(user);
// Always performs INSERT, auto-generated ID set on entity
```

### update() vs modify()

```java
// update(): Sets ALL updatable columns including nulls
// UPDATE users SET name='Alice', email=NULL, status=NULL WHERE id=1
userRepository.update(user);

// modify(): Selective update - only sets non-null fields
// UPDATE users SET name='Alice' WHERE id=1  (email, status preserved)
User partial = new User();
partial.setId(1L);
partial.setName("Alice");
userRepository.modify(partial);
```

### saveAll() - Batch INSERT

```java
List<User> users = List.of(user1, user2, user3);
userRepository.saveAll(users);
// INSERT IGNORE INTO users ... VALUES (...), (...), (...)
```

### Delete Operations

```java
userRepository.deleteById(1L);
userRepository.delete(1L);   // alias for deleteById
userRepository.remove(1L);   // alias for deleteById

// Conditional delete
userRepository.deleteWhere(Map.of("status", "INACTIVE"));
```

### Composite Key Operations

```java
@XRepository
public interface OrderItemRepository extends IXRepository<OrderItemKey, OrderItem> {}

// Lookup / delete by composite key
OrderItemKey key = new OrderItemKey();
key.setOrderId(1L);
key.setItemId(100L);

OrderItem item = repository.findOne(key);   // WHERE order_id = ? AND item_id = ?
repository.delete(key);                      // WHERE order_id = ? AND item_id = ?

// save() — all PKs set → upsert, any PK null → insert
repository.save(orderItem);

// insert() — returns OrderItemKey with both PK values
OrderItemKey savedKey = repository.insert(orderItem);
```

### Find Operations

```java
User user = userRepository.findOne(1L);            // by primary key
List<User> all = userRepository.findAll();           // all rows
boolean exists = userRepository.exists(1L);          // existence check
long total = userRepository.count();                 // total count

// Conditional queries
List<User> active = userRepository.findWhere(Map.of("status", "ACTIVE"));
User single = userRepository.findOneWhere(Map.of("email", "alice@example.com"));
long activeCount = userRepository.count(Map.of("status", "ACTIVE"));
```

## Pagination

IMPORTANT: Always use `XPagination` and `XPage` for all pagination needs. NEVER create custom pagination classes. The framework's `XResultInterceptor` automatically handles COUNT queries, ORDER BY, and LIMIT for both Repository and custom Mapper methods.

```java
XPagination pagination = new XPagination();
pagination.setPage(1);       // 1-based page number
pagination.setSize(20);      // rows per page
pagination.addOrder(new XOrder("createdAt", XDirection.DESC));
pagination.addOrder(new XOrder("name", XDirection.ASC));

// Paginated findAll
XPage<User> result = userRepository.findAll(pagination);
result.getTotalCount();   // total matching rows
result.getPage();         // current page number
result.getPageRows();     // rows for this page
result.getHasNext();      // true if more pages exist

// Paginated conditional query
XPage<User> filtered = userRepository.findWhere(
    pagination, Map.of("status", "ACTIVE")
);
```

## Query Derivation

Declare methods on the repository interface and SQL is generated automatically from the method name.

### Supported Prefixes

| Prefix | Return Type | SQL |
|---|---|---|
| `findBy` | `T` or `List<T>` | SELECT ... WHERE ... |
| `findAllBy` | `List<T>` | SELECT ... WHERE ... |
| `countBy` | `long` | SELECT COUNT(*) WHERE ... |
| `existsBy` | `boolean` | SELECT COUNT(*) WHERE ... > 0 |
| `deleteBy` | `int` | DELETE FROM ... WHERE ... |

### Examples

```java
@XRepository
public interface OrderRepository extends IXRepository<Long, Order> {
    // Single condition
    Order findByOrderNo(String orderNo);
    // WHERE order_no = ?

    // Multiple conditions with And
    List<Order> findByUserIdAndStatus(Long userId, String status);
    // WHERE user_id = ? AND status = ?

    // Count with condition
    long countByStatus(String status);
    // SELECT COUNT(*) FROM orders WHERE status = ?

    // Existence check
    boolean existsByOrderNo(String orderNo);
    // SELECT COUNT(*) FROM orders WHERE order_no = ? > 0

    // Delete with conditions
    int deleteByUserIdAndStatus(Long userId, String status);
    // DELETE FROM orders WHERE user_id = ? AND status = ?
}
```

## Service Layer Example

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User create(User user) {
        userRepository.save(user);
        return user;
    }

    public User upsert(User user) {
        user.setId(1L);
        userRepository.save(user);  // atomic upsert
        return user;
    }

    public User partialUpdate(Long id, UserUpdateRequest req) {
        User user = new User();
        user.setId(id);
        user.setName(req.getName());  // only set fields to update
        userRepository.modify(user);  // selective UPDATE
        return userRepository.findOne(id);
    }

    public List<User> findActive() {
        return userRepository.findWhere(Map.of("status", "ACTIVE"));
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

## REST Controller

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserRestController {

    private final UserService userService;

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.create(user);
    }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User user) {
        return userService.updateUser(id, user);
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    // XPagination auto-binding: ?page=1&size=10&sort=email,asc
    @GetMapping
    public XPage<User> searchUsers(@XPaginationDefault XPagination pagination,
                                   @RequestParam(value = "keyword", required = false) String keyword) {
        return userService.search(pagination, keyword);
    }
}
```

## Error Code System

The framework uses a structured error code system based on `ErrorCode` record and typed exception classes. Each exception class defines `public static final ErrorCode` constants that serve as the single source of truth for both runtime error handling and build-time documentation generation.

### ErrorCode Record

```java
public record ErrorCode(String code, String messageKey) {}
```

- `code`: A unique error identifier string (e.g., `"1"`, `"11"`, `"100"`)
- `messageKey`: An i18n message key resolved via Spring `MessageSource`

### Built-in Exception Classes

The framework provides five exception classes, each mapped to a specific HTTP status:

```java
// 400 Bad Request
public class InvalidRequestParameterException extends XRestException {
    public static final ErrorCode INVALID_REQUEST_PARAMETER = new ErrorCode("11", "server.http.error.invalid-parameter");
    public static final ErrorCode NOT_FOUND_REQUEST_BODY = new ErrorCode("12", "server.http.error.notfound-api");
    public static final ErrorCode NOT_SUPPORT_METHOD = new ErrorCode("13", "server.http.error.not-support-method");
}

// 401 Unauthorized
public class UnAuthorizedException extends XRestException {
    public static final ErrorCode NOT_FOUND_ACCESS_TOKEN = new ErrorCode("1", "server.http.error.required-auth");
    public static final ErrorCode INVALID_ACCESS_TOKEN = new ErrorCode("2", "server.http.error.invalid-auth");
    public static final ErrorCode EXPIRE_ACCESS_TOKEN = new ErrorCode("3", "server.http.error.expire-auth");
}

// 404 Not Found
public class NotFoundException extends XRestException {
    public static final ErrorCode NOT_FOUND_API = new ErrorCode("100", "server.http.error.notfound-api");
}

// 500 Internal Server Error
public class UnknownServerException extends XRestException {
    public static final ErrorCode UNKNOWN_SERVER_EXCEPTION = new ErrorCode("999", "server.http.error.unknown-server-error");
}

// 504 Gateway Timeout
public class UnavailableServerException extends XRestException {
    public static final ErrorCode UNAVAILABLE_SERVICE = new ErrorCode("900", "server.http.error.no-response-server");
}
```

### Built-in Error Codes Reference

| Code | Exception | ErrorCode Constant | HTTP Status | Message (EN) |
|---|---|---|---|---|
| `1` | `UnAuthorizedException` | `NOT_FOUND_ACCESS_TOKEN` | 401 | Authentication required. |
| `2` | `UnAuthorizedException` | `INVALID_ACCESS_TOKEN` | 401 | Invalid authentication credentials. |
| `3` | `UnAuthorizedException` | `EXPIRE_ACCESS_TOKEN` | 401 | Authentication expired. |
| `11` | `InvalidRequestParameterException` | `INVALID_REQUEST_PARAMETER` | 400 | Invalid request parameter. |
| `12` | `InvalidRequestParameterException` | `NOT_FOUND_REQUEST_BODY` | 400 | API not found. |
| `13` | `InvalidRequestParameterException` | `NOT_SUPPORT_METHOD` | 400 | HTTP method not supported. |
| `100` | `NotFoundException` | `NOT_FOUND_API` | 404 | API not found. |
| `900` | `UnavailableServerException` | `UNAVAILABLE_SERVICE` | 504 | No response from server. |
| `999` | `UnknownServerException` | `UNKNOWN_SERVER_EXCEPTION` | 500 | Unknown server error. |

### Throwing Exceptions with ErrorCode

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User getUser(Long id) {
        User user = userRepository.findOne(id);
        if (user == null) {
            // Use built-in ErrorCode constant
            throw new NotFoundException(NotFoundException.NOT_FOUND_API);
        }
        return user;
    }

    public void validateToken(String token) {
        if (token == null) {
            throw new UnAuthorizedException(UnAuthorizedException.NOT_FOUND_ACCESS_TOKEN);
        }
        if (isExpired(token)) {
            // With additional description
            throw new UnAuthorizedException(
                UnAuthorizedException.EXPIRE_ACCESS_TOKEN,
                "Token expired at 2024-01-01"
            );
        }
    }

    public void validateRequest(UserCreateRequest req) {
        if (req.getEmail() == null) {
            throw new InvalidRequestParameterException(
                InvalidRequestParameterException.INVALID_REQUEST_PARAMETER,
                "Email is required"
            );
        }
    }
}
```

### Defining Custom Exception with ErrorCode

Create your own exception class by extending `XRestException` and defining `ErrorCode` constants:

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

Add corresponding message properties for i18n:

```properties
# messages.properties (English)
user.error.duplicate-email=Email already exists.
user.error.inactive-account=Account is inactive.

# messages_ko.properties (Korean)
user.error.duplicate-email=이미 존재하는 이메일입니다.
user.error.inactive-account=비활성화된 계정입니다.
```

### Error Response Format (ApiError)

All exceptions are caught by `XExceptionHandler` (`@RestControllerAdvice`) and converted to a standard `ApiError` JSON response:

```json
{
    "code": "2001",
    "message": "Email already exists.",
    "description": "alice@example.com already exists",
    "data": null
}
```

| Field | Type | Description |
|---|---|---|
| `code` | `String` | Error code from `ErrorCode.code()` |
| `message` | `String` | i18n-resolved message from `MessageSource` |
| `description` | `String` | Additional detail (optional) |
| `data` | `Object` | Attached data, e.g., validation error fields (optional) |
| `stackTrace` | `String` | Exception stack trace (only in non-prod profiles) |

### i18n Message Resolution

The `XExceptionHandler` resolves messages using Spring `MessageSource`:

```java
messageSource.getMessage(code, args, code, locale)
```

- The third argument `code` is the default message — if the key is not found, the key string itself is returned (no `NoSuchMessageException`)
- Language is determined by the `Accept-Language` header (configurable via `axim.rest.message.language-header`)
- Default language: `ko-KR` (configurable via `axim.rest.message.default-language`)

```properties
# application.properties
axim.rest.message.default-language=ko-KR
axim.rest.message.language-header=Accept-Language
```

### Validation Error Handling

Spring `@Valid` / `BindException` errors are automatically handled with field-level error details in the `data` field:

```java
@PostMapping("/users")
public User createUser(@Valid @RequestBody UserCreateRequest req) {
    return userService.create(req);
}
```

```json
{
    "code": "422",
    "message": "Invalid request parameter.",
    "description": "Validation failed for object='userCreateRequest'...",
    "data": [
        { "field": "email", "errorMessage": "Email is required." },
        { "field": "name", "errorMessage": "Name must not be blank." }
    ]
}
```

### Error Propagation via REST Client Proxy

When calling external services via `@XRestService`, server errors are propagated transparently:

```
Server error → XExceptionHandler → ApiError JSON response
  → XRestClient receives → parses ApiError → throws XRestException
  → Caller's XExceptionHandler catches → returns same HTTP status + ApiError
```

The original HTTP status code (400, 401, 404, 500, etc.) is preserved across the proxy boundary.

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

## Query Strategy: When to Use Repository vs Custom Mapper

The framework intentionally provides two query layers. Understanding when to use each is essential:

| Need | Use | Why |
|---|---|---|
| Exact-match `=` conditions | `@XRepository` query derivation | Auto-generated, zero boilerplate |
| CRUD (save, update, delete) | `@XRepository` | Built-in methods |
| LIKE, BETWEEN, range queries | `@Mapper` | Query derivation only supports `=` |
| JOIN (any multi-table query) | `@Mapper` | Repository is single-table only |
| OR conditions | `@Mapper` | Query derivation only supports `And` |
| IN clause | `@Mapper` | Not supported in query derivation |
| Subqueries | `@Mapper` | Not supported in query derivation |
| GROUP BY / aggregation | `@Mapper` | Not supported in query derivation |

**Key principle:** `@XRepository` handles simple single-table exact-match queries. For anything beyond that, use a standard MyBatis `@Mapper` — this is by design, not a limitation.

## Custom Mapper for Complex Queries

For queries beyond what query derivation supports, create a standard MyBatis `@Mapper` interface. The framework's `XResultInterceptor` seamlessly integrates custom mappers with `XPagination`.

### Basic Custom Mapper (No Pagination)

```java
@Mapper
public interface UserMapper {
    @Select("SELECT * FROM users WHERE email LIKE CONCAT('%', #{keyword}, '%')")
    List<User> searchByEmail(@Param("keyword") String keyword);

    @Select("SELECT u.*, o.order_count FROM users u " +
            "LEFT JOIN (SELECT user_id, COUNT(*) as order_count FROM orders GROUP BY user_id) o " +
            "ON u.id = o.user_id WHERE u.status = #{status}")
    List<Map<String, Object>> findUsersWithOrderCount(@Param("status") String status);
}
```

### Custom Mapper with Pagination

The framework's `XResultInterceptor` detects `XPagination` in mapper method parameters and automatically wraps your query with COUNT, ORDER BY, and LIMIT. You only write the base SELECT.

**Three rules for paginated custom mapper methods:**
1. First parameter: `XPagination pagination`
2. Last parameter: `Class<?> cls` (pass the entity class at call site)
3. Return type: `XPage<T>`

Do NOT include ORDER BY or LIMIT in your SQL — the interceptor adds them.

```java
@Mapper
public interface OrderMapper {

    // LIKE search with pagination
    @Select("SELECT * FROM orders WHERE product_name LIKE CONCAT('%', #{keyword}, '%')")
    XPage<Order> searchOrders(XPagination pagination,
                              @Param("keyword") String keyword, Class<?> cls);

    // BETWEEN range query
    @Select("SELECT * FROM orders WHERE created_at BETWEEN #{from} AND #{to}")
    XPage<Order> findByDateRange(XPagination pagination,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to, Class<?> cls);

    // JOIN with pagination
    @Select("SELECT o.*, u.name AS user_name FROM orders o " +
            "INNER JOIN users u ON o.user_id = u.id " +
            "WHERE o.status = #{status}")
    XPage<OrderWithUser> findOrdersWithUser(XPagination pagination,
                                            @Param("status") String status, Class<?> cls);

    // Complex: LIKE + JOIN + multiple conditions
    @Select("SELECT o.*, u.name AS user_name FROM orders o " +
            "INNER JOIN users u ON o.user_id = u.id " +
            "WHERE o.status = #{status} " +
            "AND o.product_name LIKE CONCAT('%', #{keyword}, '%')")
    XPage<OrderWithUser> searchOrdersWithUser(XPagination pagination,
                                              @Param("status") String status,
                                              @Param("keyword") String keyword, Class<?> cls);

    // Dynamic SQL with <script>
    @Select("<script>" +
            "SELECT * FROM orders WHERE 1=1 " +
            "<if test='status != null'>AND status = #{status}</if> " +
            "<if test='keyword != null'>AND product_name LIKE CONCAT('%', #{keyword}, '%')</if> " +
            "</script>")
    XPage<Order> searchWithFilters(XPagination pagination,
                                   @Param("status") String status,
                                   @Param("keyword") String keyword, Class<?> cls);
}
```

### How to Call Paginated Mapper Methods

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;  // simple CRUD
    private final OrderMapper orderMapper;           // complex queries

    public XPage<Order> search(XPagination pagination, String keyword) {
        if (keyword != null) {
            // Pass Order.class as the last argument for result type mapping
            return orderMapper.searchOrders(pagination, keyword, Order.class);
        }
        return orderRepository.findAll(pagination);
    }

    public XPage<OrderWithUser> searchWithUser(XPagination pagination, String status, String keyword) {
        return orderMapper.searchOrdersWithUser(pagination, status, keyword, OrderWithUser.class);
    }
}
```

### Controller Pattern

Always use `@XPaginationDefault XPagination` for pagination parameter binding. Never create custom pagination request classes.

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    // Pagination: ?page=1&size=20&sort=createdAt,desc
    @GetMapping
    public XPage<Order> searchOrders(@XPaginationDefault XPagination pagination,
                                     @RequestParam(required = false) String keyword) {
        return orderService.search(pagination, keyword);
    }
}
```

### Pagination Rules Summary

| DO | DON'T |
|---|---|
| Use `XPagination` for pagination input | Create custom PageRequest / PaginationDTO classes |
| Use `XPage<T>` for pagination output | Create custom PageResponse / PaginationResult classes |
| Use `@XPaginationDefault` in controllers | Manually parse page/size from request params |
| Let XResultInterceptor handle COUNT/LIMIT | Add COUNT query or LIMIT clause in mapper SQL |
| Pass `Class<?>` as last param for mapper pagination | Omit the Class parameter (result mapping will fail) |

## Annotations Reference

| Annotation | Target | Description |
|---|---|---|
| `@XEntity(value, schema)` | Class | Maps class to database table |
| `@XColumn(value, isPrimaryKey, isAutoIncrement, insert, update)` | Field | Maps field to column with options |
| `@XDefaultValue(value, updateValue, isDBDefaultUsed, isDBValue)` | Field | Default values for INSERT/UPDATE |
| `@XIgnoreColumn` | Field | Excludes field from DB mapping |
| `@XRepository` | Interface | Marks repository for proxy generation |
| `@XRepositoryScan(basePackages)` | Class | Scans for @XRepository interfaces |

## Repository API Reference

| Method | Return | Description |
|---|---|---|
| `save(entity)` | `K` | Upsert: PK null=INSERT, PK present=INSERT ON DUPLICATE KEY UPDATE (Composite: all PKs set → upsert) |
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
| `count()` | `long` | Total row count |
| `count(Map)` | `long` | Conditional row count |
| `deleteById(key)` | `int` | Delete by primary key |
| `deleteWhere(Map)` | `int` | Delete by conditions |
