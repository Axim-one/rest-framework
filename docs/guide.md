# Axim REST Framework - Usage Guide

A lightweight Spring Boot + MyBatis REST framework with annotation-based entity mapping, repository proxy pattern, query derivation, and pagination.

## Critical Rules

These rules MUST be followed when using this framework:

- SECURITY: `axim.rest.session.secret-key` MUST be set in production. Without it, tokens have NO signature — anyone can forge a session token.
- SECURITY: Set `spring.profiles.active=prod` in production. Non-prod profiles log full request bodies including passwords.
- `@XColumn` is only needed for: primary keys, custom column names, or insert/update control. Regular fields auto-map via camelCase → snake_case — do NOT add `@XColumn` to every field.
- `@XDefaultValue(value="X")` alone does NOT work — `isDBDefaultUsed` defaults to `true`, so the value is ignored. Must set `isDBDefaultUsed=false` for literal values.
- `@XRestServiceScan` is required on the application class when using `@XRestService` declarative REST clients.
- `XWebClient` beans can be registered via `axim.web-client.services.{name}={url}` in properties, then injected with `@Qualifier`.
- Session token format is NOT JWT — it uses custom `Base64(payload).HmacSHA256(signature)`. Do not use JWT libraries.
- JSON date format is `yyyy-MM-dd HH:mm:ss`, not ISO 8601.
- `XSessionResolver` auto-detects `SessionData` subclass parameters — no annotation required on the controller parameter.
- `XPagination` defaults: `page=1` (1-indexed), `size=20`. `@XPaginationDefault` 없이도 기본값 적용됨. Sort without direction defaults to ASC.
- MANDATORY: Every member variable (Entity, DTO, Request, Response, VO) and every enum item MUST have a detailed Javadoc comment including purpose, example values, format rules, constraints, and allowed values.

## Installation

### Gradle

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Axim-one.rest-framework:core:1.2.2'
    implementation 'com.github.Axim-one.rest-framework:rest-api:1.2.2'
    implementation 'com.github.Axim-one.rest-framework:mybatis:1.2.2'
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
        <version>1.2.2</version>
    </dependency>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>rest-api</artifactId>
        <version>1.2.2</version>
    </dependency>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>mybatis</artifactId>
        <version>1.2.2</version>
    </dependency>
</dependencies>
```

## Application Setup

CRITICAL: All annotations below are required on the main application class. If you use `@XRestService` REST clients, also add `@XRestServiceScan`.

```java
@ComponentScan({"one.axim.framework.rest", "one.axim.framework.mybatis", "com.myapp"})
@SpringBootApplication
@XRepositoryScan("com.myapp.repository")
@MapperScan({"one.axim.framework.mybatis.mapper", "com.myapp.mapper"})
@XRestServiceScan("com.myapp.client")  // Only if using @XRestService REST clients
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

| Annotation | Required | Purpose |
|---|---|---|
| `@ComponentScan` | **Yes** | Must include `one.axim.framework.rest`, `one.axim.framework.mybatis`, and your app packages |
| `@XRepositoryScan` | **Yes** | Scans for `@XRepository` interfaces (your repository package) |
| `@MapperScan` | **Yes** | Must include `one.axim.framework.mybatis.mapper` (framework internal) + your mapper packages |
| `@XRestServiceScan` | If using REST client | Scans for `@XRestService` interfaces and creates JDK proxy beans |

### application.properties — Complete Reference

```properties
# ── DataSource ──
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# ── MyBatis ──
mybatis.config-location=classpath:mybatis-config.xml

# ── Framework: HTTP Client (optional) ──
axim.rest.client.pool-size=200                    # Max HTTP connection pool size (default: 200)
axim.rest.client.connection-request-timeout=30    # Connection request timeout in seconds (default: 30)
axim.rest.client.response-timeout=30              # Response timeout in seconds (default: 30)
axim.rest.debug=false                             # Enable REST client request/response logging (default: false)

# ── Framework: Gateway Routing (optional) ──
axim.rest.gateway.host=http://api-gateway:8080    # Gateway base URL (enables gateway mode for @XRestService)

# ── Framework: XWebClient Bean Registration (optional) ──
axim.web-client.services.userClient=http://user-service:8080      # Creates "userClient" XWebClient bean
axim.web-client.services.orderClient=http://order-service:8080    # Creates "orderClient" XWebClient bean

# ── Framework: Session / Token (optional) ──
axim.rest.session.secret-key=your-hmac-secret-key # HMAC-SHA256 signing key (if omitted, tokens are unsigned)
axim.rest.session.token-expire-days=90            # Token expiration in days (default: 90)

# ── Framework: i18n Messages (optional) ──
axim.rest.message.default-language=ko-KR          # Default locale (default: ko-KR)
axim.rest.message.language-header=Accept-Language  # HTTP header for language detection (default: Accept-Language)
spring.messages.basename=messages                  # Application message file prefix (default: messages)
spring.messages.encoding=UTF-8                     # Message file encoding (default: UTF-8)
```

### mybatis-config.xml

All three elements (objectFactory, plugins, mappers) are **required** for the framework to function correctly.

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

    <!-- REQUIRED: Custom object factory for entity instantiation -->
    <objectFactory type="one.axim.framework.mybatis.plugin.XObjectFactory"/>

    <!-- REQUIRED: Interceptor for pagination (COUNT, ORDER BY, LIMIT) and result mapping -->
    <plugins>
        <plugin interceptor="one.axim.framework.mybatis.plugin.XResultInterceptor"/>
    </plugins>

    <!-- REQUIRED: Framework internal mapper for CRUD SQL execution -->
    <mappers>
        <mapper class="one.axim.framework.mybatis.mapper.CommonMapper"/>
    </mappers>
</configuration>
```

| Element | Required | Purpose |
|---|---|---|
| `XObjectFactory` | **Yes** | Creates entity instances during result mapping |
| `XResultInterceptor` | **Yes** | Handles pagination (COUNT/ORDER BY/LIMIT) and dynamic result type mapping |
| `CommonMapper` | **Yes** | Internal mapper used by `@XRepository` proxy for all CRUD operations |
| `mapUnderscoreToCamelCase` | **Yes** | Enables MyBatis automatic snake_case → camelCase result mapping |
| `callSettersOnNulls` | Recommended | Calls setters even for NULL columns (prevents missing fields in result) |

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
// page=1, size=20 by default — ready to use immediately
XPagination pagination = new XPagination();
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

## Argument Resolvers

The framework registers two argument resolvers via `XWebMvcConfiguration`. These automatically inject `XPagination` and `SessionData` subclasses into controller method parameters.

### XPaginationResolver

Resolves `XPagination` parameters from HTTP query strings. Use `@XPaginationDefault` to set defaults.

#### Defaults

`XPagination` has built-in defaults: **`page=1` (1-indexed), `size=20`**. These apply everywhere — controllers, services, manual construction. `@XPaginationDefault` overrides these per-endpoint when needed.

| Constant | Value | Description |
|---|---|---|
| `XPagination.DEFAULT_PAGE` | `1` | First page (1-indexed) |
| `XPagination.DEFAULT_SIZE` | `20` | Rows per page |

#### @XPaginationDefault Attributes

| Attribute | Default | Description |
|---|---|---|
| `page` | `1` | Page number (1-based) |
| `size` | `20` | Rows per page |
| `offset` | `0` | Alternative to page (row offset) |
| `column` | `""` (none) | Default sort column (camelCase field name) |
| `direction` | `DESC` | Default sort direction |

#### Query Parameter Mapping

```
GET /api/v1/users?page=2&size=20&sort=createdAt,DESC&sort=name,ASC
                  ─────  ──────  ──────────────────  ──────────────
                  page    size   sort (multiple allowed)
```

| Query Param | Type | Behavior |
|---|---|---|
| `page` | int | Page number (1-based). Default: 1 |
| `size` | int | Rows per page |
| `offset` | int | Row offset (alternative to page-based pagination) |
| `sort` | string[] | Format: `column,DIRECTION` or `column` (default ASC). Multiple allowed |

#### Sort Parsing Rules

```
?sort=createdAt,DESC          → XOrder("createdAt", DESC)
?sort=name                    → XOrder("name", ASC)        ← direction omitted → defaults to ASC
?sort=createdAt,DESC&sort=name,ASC  → two XOrder objects (multi-sort)
```

#### Priority Rules

- `page`, `size` always have defaults (`1`, `20`) — query parameters override them
- `offset`-based pagination: set `offset` directly; `getOffset()` returns `(page-1)*size` when page > 0
- `"undefined"` and `"null"` string values are treated as absent (useful for frontend frameworks)

#### Controller Examples

```java
// With defaults: page=1, size=20, sort by createdAt DESC
@GetMapping
public XPage<User> listUsers(
        @XPaginationDefault(size = 20, column = "createdAt", direction = XDirection.DESC)
        XPagination pagination) {
    return userRepository.findAll(pagination);
}

// With custom defaults
@GetMapping("/recent")
public XPage<User> recentUsers(
        @XPaginationDefault(page = 1, size = 50, column = "createdAt", direction = XDirection.DESC)
        XPagination pagination) {
    return userRepository.findAll(pagination);
}

// Offset-based pagination
@GetMapping("/scroll")
public XPage<User> scrollUsers(
        @XPaginationDefault(offset = 0, size = 20)
        XPagination pagination) {
    return userRepository.findAll(pagination);
}
```

### XSessionResolver

Resolves any `SessionData` subclass parameter from the token HTTP header. **No annotation required** — the resolver detects any parameter whose type extends `SessionData`.

The default header is `Access-Token`, customizable via `getAccessTokenHeader()` override (see [Custom Token Header](#custom-token-header)).

#### How It Works

1. Controller method has a `SessionData` subclass parameter (e.g., `UserSession`)
2. Resolver extracts token header from the request (default: `Access-Token`)
3. `XAccessTokenParseHandler.validateSession()` parses and validates the token
4. If valid: deserialized session object is injected
5. If missing/invalid/expired: throws `UnAuthorizedException` (401)

#### Requirements

- `XAccessTokenParseHandler` bean must exist (auto-configured if `axim.rest.session.secret-key` is set, or provide a custom `@Component` implementation)
- If `XAccessTokenParseHandler` bean is not registered, resolver returns `null` (no error thrown — session is simply unavailable)

#### Controller Examples

```java
// Any SessionData subclass is auto-resolved — no annotation needed
@GetMapping("/me")
public UserProfile getMyProfile(UserSession session) {
    return userService.getProfile(session.getUserId());
}

// Optional session: use @Nullable or check for null
// (only if XAccessTokenParseHandler is not registered)
@GetMapping("/public")
public Content getContent(@Nullable UserSession session) {
    if (session != null) {
        return contentService.getPersonalized(session.getUserId());
    }
    return contentService.getDefault();
}
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

The framework provides JDK Proxy-based declarative REST clients via `@XRestService` and `@XRestAPI`. Requires `@XRestServiceScan` on the application class.

### Direct Mode vs Gateway Mode

Two URL construction modes are available:

```java
// Direct Mode — host is specified, serviceName is just a logical identifier
// URL: ${host} + @XRestAPI.value()
// Example: http://localhost:8081/users/1
@XRestService(value = "user-service", host = "${USER_SERVICE_HOST:http://localhost:8081}")
public interface UserServiceClient {
    @XRestAPI(value = "/users/{id}", method = XHttpMethod.GET)
    User getUser(@PathVariable("id") Long id);
}

// Gateway Mode — host is omitted, routes through API gateway
// URL: ${axim.rest.gateway.host} + serviceName + version + @XRestAPI.value()
// Example: http://api-gateway:8080/user-service/v1/users/1
@XRestService(value = "user-service", version = "v1")
public interface UserServiceClient {
    @XRestAPI(value = "/users/{id}", method = XHttpMethod.GET)
    User getUser(@PathVariable("id") Long id);
}
```

| Mode | When to Use | URL Pattern |
|---|---|---|
| **Direct** | Calling a specific service directly | `{host}{path}` |
| **Gateway** | Microservice architecture with API gateway | `{gatewayHost}/{serviceName}/{version}{path}` |

### @XRestAPI Method Parameter Annotations

```java
@XRestService(value = "order-service", host = "${ORDER_SERVICE_HOST}")
public interface OrderServiceClient {

    // @PathVariable — replaces {id} in URL path
    @XRestAPI(value = "/orders/{id}", method = XHttpMethod.GET)
    Order getOrder(@PathVariable("id") Long id);

    // @RequestBody — serialized as JSON request body
    @XRestAPI(value = "/orders", method = XHttpMethod.POST)
    Order createOrder(@RequestBody OrderCreateRequest request);

    // @RequestParam — appended as query parameters (?status=ACTIVE&keyword=test)
    @XRestAPI(value = "/orders", method = XHttpMethod.GET)
    List<Order> searchOrders(@RequestParam("status") String status,
                             @RequestParam("keyword") String keyword);

    // @RequestHeader — set as HTTP headers
    @XRestAPI(value = "/orders", method = XHttpMethod.GET)
    List<Order> getOrders(@RequestHeader("X-Tenant-Id") String tenantId);

    // XPagination — automatically converted to query params
    // Becomes: ?page=1&size=20&sort=createdAt,DESC
    @XRestAPI(value = "/orders", method = XHttpMethod.GET)
    XPage<Order> listOrders(XPagination pagination);

    // Combined: multiple annotation types
    @XRestAPI(value = "/orders/{id}", method = XHttpMethod.PUT)
    Order updateOrder(@PathVariable("id") Long id,
                      @RequestBody OrderUpdateRequest request,
                      @RequestHeader("Access-Token") String token);
}
```

### Injecting and Using REST Clients

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderServiceClient orderClient;  // Auto-injected proxy

    public Order getOrder(Long id) {
        return orderClient.getOrder(id);
    }

    public XPage<Order> listOrders(XPagination pagination) {
        // XPagination is automatically converted to query params
        return orderClient.listOrders(pagination);
    }
}
```

### Error Handling in REST Client

Server errors propagate transparently through the proxy. The original HTTP status and ApiError are preserved.

```java
try {
    Order order = orderClient.getOrder(id);
} catch (XRestException e) {
    e.getStatus();       // Original HTTP status (400, 404, 500, etc.)
    e.getCode();         // Error code string from ApiError
    e.getMessage();      // Error message
    e.getDescription();  // Additional description
}
```

### REST Client JSON Serialization

The framework's ObjectMapper is configured with:
- **Date format:** `yyyy-MM-dd HH:mm:ss` (NOT ISO 8601)
- **Unknown properties:** Ignored (`FAIL_ON_UNKNOWN_PROPERTIES = false`)

```java
// Dates are serialized/deserialized as "yyyy-MM-dd HH:mm:ss"
// ✓ "2024-01-15 14:30:00"
// ✗ "2024-01-15T14:30:00Z" (ISO 8601 — not used)
```

## XWebClient (RestClient-based Alternative)

For programmatic HTTP calls (not declarative proxy), use `XWebClient` which uses Spring's newer `RestClient` API.

### Option 1: Declarative Bean Registration via Properties

Register `XWebClient` beans by name in `application.properties`:

```properties
# Each entry creates a named XWebClient bean (lazy-initialized, cached)
axim.web-client.services.userClient=http://user-service:8080
axim.web-client.services.orderClient=http://order-service:8080
axim.web-client.services.paymentClient=http://payment-service:8080
```

Inject by bean name using `@Qualifier`:

```java
@Service
@RequiredArgsConstructor
public class ExternalApiService {

    @Qualifier("userClient")
    private final XWebClient userClient;

    @Qualifier("orderClient")
    private final XWebClient orderClient;

    public User getUser(Long id) {
        return userClient.get("/users/{id}", User.class, id);
    }

    public Order getOrder(Long id) {
        return orderClient.get("/orders/{id}", Order.class, id);
    }
}
```

### Option 2: Programmatic via XWebClientFactory

Use `XWebClientFactory` to create clients dynamically (clients are cached per baseUrl):

```java
@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final XWebClientFactory webClientFactory;

    public User getUser(Long id) {
        XWebClient client = webClientFactory.create("http://external-api.com");
        return client.get("/users/{id}", User.class, id);
    }

    public User createUser(UserRequest request) {
        XWebClient client = webClientFactory.create("http://external-api.com");
        return client.post("/users", request, User.class);
    }
}
```

### XWebClient API Reference

```java
// Simple API — Class<T> or ParameterizedTypeReference<T>
client.get("/users/{id}", User.class, id);
client.post("/users", body, User.class);
client.put("/users/{id}", body, User.class, id);
client.patch("/users/{id}", body, User.class, id);
client.delete("/users/{id}", Void.class, id);

// Generic types
client.get("/users", new ParameterizedTypeReference<List<User>>() {});

// Builder API — for headers and complex requests
client.spec()
        .get("/users?keyword=" + keyword)
        .header("X-API-Key", "my-key")
        .header("Authorization", "Bearer " + token)
        .body(requestBody)
        .retrieve(new ParameterizedTypeReference<List<User>>() {});
```

### XWebClient vs @XRestService

| Feature | `@XRestService` (Declarative Proxy) | `XWebClient` (Programmatic) |
|---|---|---|
| Style | Interface + annotations | Direct method calls |
| Bean creation | `@XRestServiceScan` | Properties or `XWebClientFactory` |
| Best for | Internal microservice calls | External API calls, dynamic URLs |
| Pagination | Auto XPagination → query params | Manual query string |
| Gateway routing | Built-in | Manual URL construction |
```

## Session / Token Authentication

The framework provides a built-in token system. **This is NOT JWT** — it uses a custom `Base64(payload).HmacSHA256(signature)` format.

### Token Format

```
{BASE64_ENCODED_PAYLOAD}.{HMAC_SHA256_SIGNATURE}

Example:
eyJ1c2VySWQiOjEsInNlc3Npb25JZCI6ImFiYzEyMyJ9.a2b3c4d5e6f7...

NOT JWT format (header.payload.signature)
```

### Defining Custom Session Data

Extend `SessionData` to define your session model:

```java
@Data
public class UserSession extends SessionData {

    /** 사용자 고유 ID */
    private Long userId;

    /** 사용자 이름 */
    private String userName;

    /** 사용자 권한 목록 (예시: ["ADMIN", "USER"]) */
    private List<String> roles;
}
```

`SessionData` base fields (auto-managed):
- `sessionId` (String) — unique session identifier
- `createDate` (String) — format: `yyyyMMddHHmmss`, used for expiry check

### Generating Tokens (Login)

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final XAccessTokenParseHandler tokenHandler;

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody LoginRequest request) {
        // Authenticate user...

        UserSession session = new UserSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(authenticatedUser.getId());
        session.setUserName(authenticatedUser.getName());
        session.setRoles(authenticatedUser.getRoles());

        String token = tokenHandler.generateAccessToken(session);
        return Map.of("accessToken", token);
    }
}
```

### Using Session in Controllers

Session data is automatically resolved from the token HTTP header (default: `Access-Token`):

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    // UserSession is auto-resolved from token header (default: "Access-Token")
    // If token is missing → 401 (NOT_FOUND_ACCESS_TOKEN)
    // If token is invalid → 401 (INVALID_ACCESS_TOKEN)
    // If token is expired → 401 (EXPIRE_ACCESS_TOKEN)
    @GetMapping("/me")
    public UserProfile getMyProfile(UserSession session) {
        return userService.getProfile(session.getUserId());
    }
}
```

### Session Configuration

```properties
# HMAC-SHA256 signing key — MUST be set in production
axim.rest.session.secret-key=your-secret-key

# Token expiration in days (default: 90)
axim.rest.session.token-expire-days=90
```

**SECURITY WARNING: `secret-key` MUST be configured in production.** If omitted, tokens are generated WITHOUT signature verification — the payload is simply Base64-decoded without any integrity check. This means anyone can forge a valid session token by crafting a Base64-encoded JSON payload. Always set a strong, unique secret key for production environments.

```properties
# ✗ DANGEROUS — no secret-key → tokens can be forged
# axim.rest.session.secret-key=

# ✓ SECURE — HMAC-SHA256 signature verification enabled
axim.rest.session.secret-key=a-strong-random-secret-key-at-least-32-chars
```

### Custom Token Handler

Override `XBaseAccessTokenHandler` for custom token logic:

```java
@Component
public class CustomTokenHandler extends XBaseAccessTokenHandler {
    // Customize token generation/parsing as needed
}
```

### Custom Token Header

The default header is `Access-Token`. Override `getAccessTokenHeader()` to use a different header:

```java
@Component
public class CustomTokenHandler extends XBaseAccessTokenHandler {

    @Override
    public String getAccessTokenHeader() {
        return "Authorization";  // Use standard Authorization header
    }
}
```

This affects all token operations: session resolution, `validateSession()`, and `XSessionController.hasSession()`.

## i18n Message Source

The framework uses a hierarchical message source for error message localization:

```
Application messages (messages.properties)
         ↓ (overrides)
Framework messages (framework-messages.properties)
```

### Setup

Create message files in `src/main/resources/`:

```properties
# messages.properties (English — application custom messages)
user.error.duplicate-email=Email already exists.
user.error.inactive-account=Account is inactive.

# messages_ko.properties (Korean)
user.error.duplicate-email=이미 존재하는 이메일입니다.
user.error.inactive-account=비활성화된 계정입니다.
```

The framework provides built-in messages in `framework-messages.properties`:

```properties
server.http.error.invalid-parameter=Invalid request parameter.
server.http.error.not-support-method=HTTP method not supported.
server.http.error.required-auth=Authentication required.
server.http.error.invalid-auth=Invalid authentication credentials.
server.http.error.expire-auth=Authentication expired.
server.http.error.notfound-api=API not found.
server.http.error.server-error=Internal server error.
server.http.error.unknown-server-error=Unknown server error.
server.http.error.no-response-server=No response from server.
```

Application messages override framework messages when the same key is used. If a key is not found in any message source, the key string itself is returned (no exception thrown).

## Security Warnings

### 1. Session Secret Key — Token Forgery Risk

If `axim.rest.session.secret-key` is NOT configured, session tokens are generated **without HMAC signature**. The token payload is simply Base64-encoded JSON with no integrity check, meaning **anyone can forge a valid session token**.

```properties
# ✗ DANGEROUS — production without secret-key
# Attacker can create: Base64({"userId":1,"sessionId":"fake"}) → valid token
# axim.rest.session.secret-key=

# ✓ REQUIRED for production — enables HMAC-SHA256 signature verification
axim.rest.session.secret-key=a-strong-random-secret-key-at-least-32-chars
```

**Rules:**
- ALWAYS set `secret-key` in production environments
- Use a cryptographically strong random string (minimum 32 characters)
- NEVER commit the actual secret key to source control — use environment variables or secret management
- Store in `application-prod.properties` or inject via `${SESSION_SECRET_KEY}` environment variable

### 2. Request Body Logging in Development Profile

The framework's `XRequestFilter` logs full request bodies (including JSON payloads) when the active Spring profile is NOT `prod`. This is useful for debugging but poses a security risk:

- **Passwords, credit card numbers, and other sensitive fields are logged as-is** — no field-level masking
- HTTP headers like `Authorization` and token headers are masked (`***`), but **request body fields are NOT**

**Rules:**
- NEVER use development/default profile in production — always set `spring.profiles.active=prod`
- Be aware that login endpoints (`/auth/login`) will log plaintext passwords in non-prod profiles
- If you need request logging in production, implement a custom filter with field-level masking

```properties
# ✗ DANGEROUS — non-prod profile logs all request bodies including passwords
spring.profiles.active=dev

# ✓ SAFE — prod profile disables request body logging and stack traces in error responses
spring.profiles.active=prod
```

### 3. Demo Credentials — Do Not Copy

Demo module configuration files contain hardcoded database credentials. These are for local development only. NEVER copy demo credentials into production configuration.

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

**Two rules for paginated custom mapper methods:**
1. Include `XPagination pagination` as a parameter
2. Return type: `XPage<T>` (the entity type is inferred from the generic parameter)

Do NOT include ORDER BY or LIMIT in your SQL — the interceptor adds them.

```java
@Mapper
public interface OrderMapper {

    // LIKE search with pagination
    @Select("SELECT * FROM orders WHERE product_name LIKE CONCAT('%', #{keyword}, '%')")
    XPage<Order> searchOrders(XPagination pagination,
                              @Param("keyword") String keyword);

    // BETWEEN range query
    @Select("SELECT * FROM orders WHERE created_at BETWEEN #{from} AND #{to}")
    XPage<Order> findByDateRange(XPagination pagination,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    // JOIN with pagination
    @Select("SELECT o.*, u.name AS user_name FROM orders o " +
            "INNER JOIN users u ON o.user_id = u.id " +
            "WHERE o.status = #{status}")
    XPage<OrderWithUser> findOrdersWithUser(XPagination pagination,
                                            @Param("status") String status);

    // Complex: LIKE + JOIN + multiple conditions
    @Select("SELECT o.*, u.name AS user_name FROM orders o " +
            "INNER JOIN users u ON o.user_id = u.id " +
            "WHERE o.status = #{status} " +
            "AND o.product_name LIKE CONCAT('%', #{keyword}, '%')")
    XPage<OrderWithUser> searchOrdersWithUser(XPagination pagination,
                                              @Param("status") String status,
                                              @Param("keyword") String keyword);

    // Dynamic SQL with <script>
    @Select("<script>" +
            "SELECT * FROM orders WHERE 1=1 " +
            "<if test='status != null'>AND status = #{status}</if> " +
            "<if test='keyword != null'>AND product_name LIKE CONCAT('%', #{keyword}, '%')</if> " +
            "</script>")
    XPage<Order> searchWithFilters(XPagination pagination,
                                   @Param("status") String status,
                                   @Param("keyword") String keyword);
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
            return orderMapper.searchOrders(pagination, keyword);
        }
        return orderRepository.findAll(pagination);
    }

    public XPage<OrderWithUser> searchWithUser(XPagination pagination, String status, String keyword) {
        return orderMapper.searchOrdersWithUser(pagination, status, keyword);
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

## Coding Conventions

### MANDATORY: Document All Member Variables and Enum Items

Every member variable in Entity classes, DTO classes, and every enum item MUST have a Javadoc comment. Comments should be as detailed as possible, including:
- **Purpose**: What this field represents
- **Examples**: Concrete example values when applicable
- **Rules/Constraints**: Validation rules, format patterns, allowed ranges, or business rules

This rule applies to ALL classes: Entity, DTO, Request, Response, VO, and Enum types. No exceptions.

#### Entity Example

```java
@Data
@XEntity("orders")
public class Order {

    /** 주문 고유 식별자 (Auto Increment) */
    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;

    /**
     * 주문 번호
     * - 형식: "ORD-{yyyyMMdd}-{6자리 시퀀스}"
     * - 예시: "ORD-20240115-000001"
     * - UNIQUE 제약조건 적용
     */
    private String orderNo;

    /**
     * 주문 상태
     * - "PENDING": 결제 대기
     * - "PAID": 결제 완료
     * - "SHIPPED": 배송 중
     * - "DELIVERED": 배송 완료
     * - "CANCELLED": 주문 취소
     * @see OrderStatus
     */
    private String status;

    /**
     * 주문 총 금액 (단위: 원, KRW)
     * - 소수점 2자리까지 허용
     * - 음수 불가
     * - 예시: 15000.00
     */
    private BigDecimal totalAmount;

    /**
     * 주문자 ID (users 테이블 FK)
     * - NULL 불가
     */
    private Long userId;

    /** 주문 생성 일시 (INSERT 시 자동 설정) */
    @XDefaultValue(value = "NOW()", isDBValue = true)
    private LocalDateTime createdAt;

    /** 주문 수정 일시 (UPDATE 시 자동 갱신) */
    @XDefaultValue(updateValue = "NOW()", isDBValue = true)
    private LocalDateTime updatedAt;
}
```

#### DTO / Request Example

```java
@Data
public class OrderCreateRequest {

    /**
     * 주문할 상품 ID 목록
     * - 최소 1개 이상 필수
     * - 예시: [1, 2, 3]
     */
    @NotEmpty
    private List<Long> productIds;

    /**
     * 배송지 주소
     * - 전체 도로명 주소 (우편번호 제외)
     * - 예시: "서울특별시 강남구 테헤란로 123 4층"
     * - 최대 200자
     */
    @NotBlank
    @Size(max = 200)
    private String shippingAddress;

    /**
     * 배송 메모 (선택사항)
     * - 예시: "부재 시 경비실에 맡겨주세요"
     * - 최대 500자, NULL 허용
     */
    @Size(max = 500)
    private String deliveryNote;

    /**
     * 결제 수단 코드
     * - "CARD": 신용/체크카드
     * - "BANK": 무통장입금
     * - "KAKAO": 카카오페이
     * - "NAVER": 네이버페이
     */
    @NotBlank
    private String paymentMethod;
}
```

#### Enum Example

```java
public enum OrderStatus {

    /** 결제 대기 — 주문이 생성되었으나 결제가 완료되지 않은 상태 */
    PENDING,

    /** 결제 완료 — 결제가 확인되어 상품 준비 중인 상태 */
    PAID,

    /** 배송 중 — 택배사에 인계되어 배송이 진행 중인 상태 */
    SHIPPED,

    /** 배송 완료 — 수령인이 상품을 수령한 상태 */
    DELIVERED,

    /** 주문 취소 — 고객 요청 또는 시스템에 의해 취소된 상태. 환불 처리 필요 */
    CANCELLED
}
```

#### Rules Summary

| Rule | Description |
|---|---|
| All member variables | Must have `/** */` Javadoc comment describing purpose |
| Example values | Include concrete examples with `예시:` or `e.g.` prefix |
| Format/Pattern | Document format rules (e.g., `"ORD-{yyyyMMdd}-{seq}"`) |
| Allowed values | List all valid values for string-coded fields |
| Constraints | Note NOT NULL, UNIQUE, max length, range limits |
| Enum items | Each item must have a comment explaining the state/meaning |
| FK references | Note the referenced table (e.g., "users 테이블 FK") |
| Units | Specify units for numeric fields (e.g., 원, KRW, %, 초) |

### @XColumn Usage Rules

`@XColumn` is NOT required on every field. The framework auto-maps fields using camelCase → snake_case conversion. Only use `@XColumn` when you need to set specific options.

#### When @XColumn IS Required

```java
// 1. Primary Key — MUST mark with @XColumn
@XColumn(isPrimaryKey = true, isAutoIncrement = true)
private Long id;

// 2. Primary Key without auto-increment (e.g., composite key)
@XColumn(isPrimaryKey = true)
private Long orderId;

// 3. Custom column name that doesn't follow camelCase → snake_case
@XColumn("usr_email_addr")
private String email;

// 4. Read-only field (excluded from INSERT and UPDATE)
@XColumn(insert = false, update = false)
private String readOnlyField;

// 5. Exclude from UPDATE only (e.g., immutable after creation)
@XColumn(update = false)
private String createdBy;

// 6. Exclude from INSERT only
@XColumn(insert = false)
private String computedField;
```

#### When @XColumn is NOT Needed (Omit It)

```java
@Data
@XEntity("users")
public class User {

    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;           // ✓ @XColumn needed — primary key

    private String email;      // ✓ auto-mapped to "email"
    private String userName;   // ✓ auto-mapped to "user_name"
    private Integer loginCount; // ✓ auto-mapped to "login_count"
    private LocalDateTime createdAt; // ✓ auto-mapped to "created_at"
}
```

#### @XColumn Quick Reference

| Situation | @XColumn | Example |
|---|---|---|
| Primary Key | **Required** | `@XColumn(isPrimaryKey = true, isAutoIncrement = true)` |
| Composite PK field | **Required** | `@XColumn(isPrimaryKey = true)` |
| Regular field (camelCase→snake_case) | **Omit** | `private String userName;` → `user_name` |
| Custom column name | **Required** | `@XColumn("usr_nm")` |
| Read-only field | **Required** | `@XColumn(insert = false, update = false)` |
| Exclude from DB entirely | Use `@XIgnoreColumn` | `@XIgnoreColumn private String temp;` |

### @XDefaultValue Pitfall: isDBDefaultUsed Defaults to true

CRITICAL: `isDBDefaultUsed` defaults to `true`. This means `@XDefaultValue(value = "ACTIVE")` will **omit the column from INSERT** and use the DB DEFAULT instead — the `value = "ACTIVE"` is silently ignored!

```java
// ✗ WRONG — isDBDefaultUsed defaults to true, so "ACTIVE" is IGNORED
// The column is omitted from INSERT, DB DEFAULT is used instead
@XDefaultValue(value = "ACTIVE")
private String status;

// ✓ CORRECT — explicitly set isDBDefaultUsed = false to use literal value
@XDefaultValue(value = "ACTIVE", isDBDefaultUsed = false)
private String status;

// ✓ CORRECT — DB expression (isDBValue=true implies isDBDefaultUsed=false behavior)
@XDefaultValue(value = "NOW()", isDBValue = true)
private LocalDateTime createdAt;

// ✓ CORRECT — intentionally use DB DEFAULT (column omitted from INSERT)
@XDefaultValue(isDBDefaultUsed = true)
private String region;

// ✓ CORRECT — updateValue only (INSERT unaffected, UPDATE auto-sets)
@XDefaultValue(updateValue = "NOW()", isDBValue = true)
private LocalDateTime updatedAt;
```

## Common Pitfalls

Mistakes frequently made by AI when using this framework:

### 1. Using Column Names Instead of Field Names

Query derivation and `findWhere()` use **Java field names** (camelCase), NOT database column names (snake_case).

```java
// ✗ WRONG — using snake_case column name
User findByUser_name(String name);
userRepository.findWhere(Map.of("user_name", "Alice"));

// ✓ CORRECT — using camelCase field name
User findByUserName(String name);
userRepository.findWhere(Map.of("userName", "Alice"));
```

### 2. findBy Return Type Determines Behavior

The return type of `findBy` methods changes the SQL behavior:

```java
// Returns single entity — adds LIMIT 1 (findOne semantics)
User findByEmail(String email);

// Returns list — no LIMIT, returns all matches (findWhere semantics)
List<User> findByEmail(String email);
```

### 3. update() Overwrites Fields with NULL

`update()` sets ALL updatable columns, including null fields. Use `modify()` for partial updates.

```java
User user = new User();
user.setId(1L);
user.setName("Alice");
// user.email is null, user.status is null

// ✗ DANGEROUS — sets email=NULL, status=NULL in DB
userRepository.update(user);

// ✓ SAFE — only sets name='Alice', other fields preserved
userRepository.modify(user);
```

### 4. Adding ORDER BY / LIMIT in Custom Mapper SQL

The `XResultInterceptor` automatically handles pagination SQL. Never add ORDER BY or LIMIT in mapper queries.

```java
// ✗ WRONG — ORDER BY and LIMIT conflict with interceptor
@Select("SELECT * FROM users WHERE status = #{status} ORDER BY created_at DESC LIMIT 20")
XPage<User> findByStatus(XPagination pagination, @Param("status") String status);

// ✓ CORRECT — only write the base SELECT
@Select("SELECT * FROM users WHERE status = #{status}")
XPage<User> findByStatus(XPagination pagination, @Param("status") String status);
```

### 5. Creating Custom Pagination Classes

NEVER create custom pagination classes. Always use the framework's `XPagination` and `XPage`.

```java
// ✗ WRONG — creating custom pagination
public class PageRequest { int page; int size; }
public class PageResponse<T> { List<T> items; long total; }

// ✓ CORRECT — use framework classes
XPagination pagination = new XPagination();
XPage<User> result = userRepository.findAll(pagination);
```

### 6. Passing Empty Map to findWhere()

`findWhere(Map)` requires a non-empty map. Use `findAll()` for unconditional queries.

```java
// ✗ WRONG — empty map throws IllegalArgumentException
userRepository.findWhere(Map.of());

// ✓ CORRECT
userRepository.findAll();
userRepository.findWhere(Map.of("status", "ACTIVE"));
```

### 7. Query Derivation Method Name Parsing

Method names are split by `And` only when preceded by lowercase and followed by uppercase. Field names with "And" in them are NOT split.

```java
// "BrandName" → single field "brandName" (no split — "dN" is lowercase+uppercase within the field)
User findByBrandName(String brandName);

// "Status" + "Name" → two fields split by "And"
List<User> findByStatusAndName(String status, String name);

// Parameter count MUST exactly match the number of parsed fields
// ✗ WRONG — 2 fields parsed but 1 parameter
List<User> findByStatusAndName(String status);
```
