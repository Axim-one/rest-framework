---
name: axim-rest-framework
description: This skill should be used when the user asks to "create an entity", "add a repository", "set up pagination", "create a REST controller", "add error handling", "configure REST client", "use XPagination", "create a custom mapper", or works with Spring Boot + MyBatis using the axim-rest-framework. Covers @XEntity, @XRepository, IXRepository, query derivation, save/modify/upsert, XPagination, XPage, error codes, i18n exceptions, @XRestService declarative REST client, and XWebClient.
---

# Axim REST Framework

Spring Boot + MyBatis lightweight REST framework. Annotation-based entity mapping and repository proxy pattern that minimizes boilerplate while keeping MyBatis SQL control.

**Version:** 1.3.0 | **Java 17+** | **Spring Boot 3.3+** | **MySQL 5.7+/8.0+** | **MyBatis 3.0+**

## Critical Rules

- **SECURITY:** `axim.rest.session.secret-key` MUST be set in production. Without it, tokens have NO signature — anyone can forge a session token.
- **SECURITY:** Set `spring.profiles.active=prod` in production. Non-prod profiles log full request bodies including passwords.
- `@XColumn` is only needed for: primary keys, custom column names, or insert/update control. Regular fields auto-map via camelCase to snake_case.
- `@XDefaultValue(value="X")` alone does NOT work — `isDBDefaultUsed` defaults to `true`, so the value is ignored. Must set `isDBDefaultUsed=false` for literal values.
- `@XRestServiceScan` is required on the application class when using `@XRestService` declarative REST clients.
- Session token format is NOT JWT — uses custom `Base64(payload).HmacSHA256(signature)`. Do not use JWT libraries.
- JSON date format is `yyyy-MM-dd HH:mm:ss`, not ISO 8601.
- `@XPaginationDefault` defaults: `page=1`, `size=10`, `direction=DESC`. Sort without direction defaults to ASC.
- MANDATORY: Every member variable (Entity, DTO, Request, Response, VO) and every enum item MUST have a detailed Javadoc comment.
- Always use `XPagination` and `XPage` for pagination. NEVER create custom pagination classes.

## Installation

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Axim-one.rest-framework:core:1.3.0'
    implementation 'com.github.Axim-one.rest-framework:rest-api:1.3.0'
    implementation 'com.github.Axim-one.rest-framework:mybatis:1.3.0'
}
```

For Maven installation, see `references/setup-and-config.md`.

## Entity Definition

```java
@Data
@XEntity("users")
public class User {
    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;

    private String email;      // auto-mapped to "email"
    private String userName;   // auto-mapped to "user_name"

    @XDefaultValue(value = "NOW()", isDBValue = true)
    private LocalDateTime createdAt;

    @XDefaultValue(updateValue = "NOW()", isDBValue = true)
    private LocalDateTime updatedAt;

    @XIgnoreColumn
    private String transientField;  // excluded from DB
}
```

| Annotation | Purpose |
|---|---|
| `@XEntity(value, schema)` | Maps class to database table |
| `@XColumn(isPrimaryKey, isAutoIncrement, insert, update)` | Column mapping options |
| `@XDefaultValue(value, updateValue, isDBDefaultUsed, isDBValue)` | Default values for INSERT/UPDATE |
| `@XIgnoreColumn` | Excludes field from DB mapping |

For entity inheritance, composite keys, @XDefaultValue patterns, and @XColumn rules, see `references/entity-and-repository.md`.

## Repository

Extend `IXRepository<K, T>` and annotate with `@XRepository`:

```java
@XRepository
public interface UserRepository extends IXRepository<Long, User> {
    User findByEmail(String email);
    List<User> findByStatus(String status);
    long countByStatus(String status);
    boolean existsByEmail(String email);
    int deleteByStatusAndName(String status, String name);
}
```

**Key methods:** `save` (upsert), `insert`, `update` (full), `modify` (selective/non-null only), `findOne`, `findAll`, `findWhere`, `deleteById`, `deleteWhere`, `exists`, `count`.

**Query derivation prefixes:** `findBy`, `findAllBy`, `countBy`, `existsBy`, `deleteBy` — only supports exact-match `=` with `And` combinator.

For full API table, CRUD examples, and composite key patterns, see `references/entity-and-repository.md`.

## Pagination

```java
// Controller with auto-binding
@GetMapping
public XPage<User> list(@XPaginationDefault XPagination pagination) {
    return userRepository.findAll(pagination);
}
// Accepts: ?page=1&size=10&sort=createdAt,DESC
```

Page is **1-indexed**. `XPagination` + `XPage<T>` are the only pagination classes — never create custom ones.

For @XPaginationDefault attributes and advanced usage, see `references/query-and-pagination.md`.

## Query Strategy: Repository vs Custom Mapper

| Query Type | Use |
|---|---|
| Exact-match `=` with `And` | `@XRepository` query derivation |
| CRUD (save, update, delete) | `@XRepository` built-in methods |
| LIKE, BETWEEN, JOIN, OR, IN, subquery, aggregation | **`@Mapper` (custom SQL)** |

When Repository cannot handle a query, create a `@Mapper` interface. Write only the base SELECT — XResultInterceptor handles COUNT, ORDER BY, LIMIT automatically:

```java
@Mapper
public interface UserMapper {
    @Select("SELECT * FROM users WHERE name LIKE CONCAT('%', #{keyword}, '%')")
    XPage<User> searchByName(XPagination pagination, @Param("keyword") String keyword);
}
```

For full custom mapper patterns and controller examples, see `references/query-and-pagination.md`.

## Error Handling

```java
public class UserException extends XRestException {
    public static final ErrorCode DUPLICATE_EMAIL = new ErrorCode("2001", "user.error.duplicate-email");

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

i18n via `messages.properties` / `messages_ko.properties`. Response format: `{"code": "2001", "message": "...", "description": "...", "data": null}`.

## REST Client

### Declarative (@XRestService)

```java
@XRestService(value = "user-service", host = "${USER_SERVICE_HOST:http://localhost:8081}")
public interface UserServiceClient {
    @XRestAPI(value = "/users/{id}", method = XHttpMethod.GET)
    User getUser(@PathVariable("id") Long id);

    @XRestAPI(value = "/users", method = XHttpMethod.POST)
    User createUser(@RequestBody UserCreateRequest request);
}
```

### Programmatic (XWebClient)

```java
@Qualifier("userClient")
private final XWebClient userClient;

User user = userClient.get("/users/{id}", User.class, id);
```

For full REST client details, XWebClient API, session/token auth, see `references/rest-client-and-auth.md`.

## Architecture

```
Application Code                    Framework Internals
----------------                    -------------------
@XRepository                        XRepositoryBeanScanner
UserRepository                           |
  extends IXRepository<K, T>        XRepositoryProxyFactoryBean
       |                                 |
  (JDK Dynamic Proxy)              XRepositoryProxy (InvocationHandler)
       |                                 |
                                    CommonMapper (@Mapper)
                                         |
                                    CrudSqlProvider (SQL Generation + Cache)
                                         |
                                    XResultInterceptor (Pagination, Result Mapping)
```

## Additional Resources

### Reference Files

For detailed patterns, examples, and advanced techniques:

- **`references/entity-and-repository.md`** — Entity mapping, annotations, @XDefaultValue, @XColumn rules, repository API, CRUD examples, composite keys, query derivation
- **`references/query-and-pagination.md`** — Custom mapper patterns, pagination details, @XPaginationDefault, controller + service examples
- **`references/rest-client-and-auth.md`** — @XRestService (Direct/Gateway mode), XWebClient, session/token authentication
- **`references/setup-and-config.md`** — Application setup, application.properties, mybatis-config.xml, Maven installation, i18n
- **`references/conventions-and-pitfalls.md`** — Javadoc conventions, common pitfalls, security warnings
