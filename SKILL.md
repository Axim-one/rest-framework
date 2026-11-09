---
name: axim-rest-framework
description: This skill should be used when the user asks to "create an entity", "add a repository", "set up pagination", "create a REST controller", "add error handling", "configure REST client", "use XPagination", "create a custom mapper", or works with Spring Boot + MyBatis using the axim-rest-framework. Covers @XEntity, @XRepository, IXRepository, query derivation, save/modify/upsert, XPagination, XPage, error codes, i18n exceptions, @XRestService declarative REST client, and XWebClient.
---

# Axim REST Framework

Spring Boot + MyBatis lightweight REST framework. Annotation-based entity mapping and repository proxy pattern that minimizes boilerplate while keeping MyBatis SQL control.

**Version:** 1.5.0 | **Java 17+** | **Spring Boot 3.3+** | **MySQL 5.7+/8.0+ or PostgreSQL 9.5+** | **MyBatis 3.0+**

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
- PostgreSQL: set `axim.mybatis.dialect=postgresql` (or rely on first-query auto-detection). Repository/CRUD code is identical; custom `@Mapper` SQL must be written in PostgreSQL syntax. One DB type per application.

## Installation

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Axim-one.rest-framework:core:1.5.0'
    implementation 'com.github.Axim-one.rest-framework:rest-api:1.5.0'
    implementation 'com.github.Axim-one.rest-framework:mybatis:1.5.0'
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

### Error Propagation (REST Client)

`@XRestService` / `XWebClient` 호출 시 원격 서비스 에러가 `XRestException`으로 전파됨. 원본 응답 바디와 서비스명이 보존됨:

```java
try {
    Order order = orderClient.getOrder(id);
} catch (XRestException e) {
    e.getStatus();            // HTTP 상태코드 (400, 404, 500 등)
    e.getCode();              // 에러 코드
    e.getMessage();           // 에러 메시지
    e.getData();              // 추가 데이터 (validation 필드 목록 등)
    e.getRawResponseBody();   // 원본 JSON 문자열 — 어떤 포맷이든 직접 파싱 가능
    e.getRemoteServiceName(); // 서비스명 (@XRestService value)
}
```

외부 API의 에러 포맷이 다를 경우, `XErrorResponseHandler` Bean으로 커스텀 파싱 등록 가능:

```java
@Component("stripe-api-error-handler")  // @XRestService(value) + "-error-handler"
public class StripeErrorHandler implements XErrorResponseHandler {
    public XRestException handle(HttpStatus status, String responseBody) {
        // 커스텀 파싱 → null 반환 시 기본 핸들러로 폴백
    }
}
```

For full error propagation details and handler patterns, see `references/rest-client-and-auth.md`.

## REST Client Strategy

Two HTTP client options. Choose based on use case:

| Situation | Use | Reason |
|---|---|---|
| Internal microservice (fixed API contract) | `@XRestService` | 선언적, 타입 안전, XPagination 자동 변환 |
| External API / Dynamic URL | `XWebClient` | 프로그래밍 방식, URL 유연, 헤더 자유 설정 |
| 파일 업로드 / Multipart | `XWebClient` (spec builder) | @XRestService는 JSON body만 지원 |
| Service-to-service 토큰 포워딩 | `@XRestService` + `@RequestHeader` | 선언적 헤더 전달 |

### @XRestService (Declarative)

Requires `@XRestServiceScan` on application class. Interface + annotation, JDK Proxy가 자동 생성.

```java
@XRestService(value = "user-service", host = "${USER_SERVICE_HOST:http://localhost:8081}")
public interface UserServiceClient {
    @XRestAPI(value = "/users/{id}", method = XHttpMethod.GET)
    User getUser(@PathVariable("id") Long id);

    @XRestAPI(value = "/users", method = XHttpMethod.POST)
    User createUser(@RequestBody UserCreateRequest request);

    @XRestAPI(value = "/users", method = XHttpMethod.GET)
    XPage<User> listUsers(XPagination pagination);  // auto -> ?page=1&size=20&sort=...

    @XRestAPI(value = "/users/{id}", method = XHttpMethod.PUT)
    User updateUser(@PathVariable("id") Long id,
                    @RequestBody UserUpdateRequest request,
                    @RequestHeader("Access-Token") String token);  // header forwarding
}
```

### XWebClient (Programmatic)

```java
// Option 1: Properties 기반 Bean
@Qualifier("userClient") private final XWebClient userClient;
User user = userClient.get("/users/{id}", User.class, id);

// Option 2: Factory로 동적 생성
XWebClient client = webClientFactory.create("https://api.external.com");
List<User> users = client.get("/users", new ParameterizedTypeReference<>() {});

// Option 3: Builder API (커스텀 헤더, 복잡한 요청)
client.spec()
    .post("/orders")
    .header("X-API-Key", apiKey)
    .body(request)
    .retrieve(Order.class);
```

For full annotation reference, parameter details, pitfalls, and patterns, see `references/rest-client-and-auth.md`.

## PostgreSQL (v1.5.0)

Default dialect is MySQL; SQL sent to MySQL is byte-identical to 1.4.x. For PostgreSQL:

```yaml
axim:
  mybatis:
    dialect: postgresql   # mysql | postgresql — omitted → auto-detected on the first query from connection metadata, fallback mysql
```

- Add the `org.postgresql:postgresql` driver in the app. `XResultInterceptor` in `mybatis-config.xml` stays mandatory (it handles generated keys on PostgreSQL).
- Mixing MySQL and PostgreSQL in one application is NOT supported (the dialect is JVM-global).

| | MySQL | PostgreSQL |
|---|---|---|
| Table identifier | `` `schema`.`table` `` | `"schema"."table"` |
| Pagination | `LIMIT offset, size` | `LIMIT size OFFSET offset` |
| `save()` with all PKs set | `ON DUPLICATE KEY UPDATE` | `ON CONFLICT (<pk columns>) DO UPDATE SET` |
| `saveAll()` | `INSERT IGNORE` | `ON CONFLICT DO NOTHING` |
| `insert()` generated id | `SELECT LAST_INSERT_ID()` | JDBC `getGeneratedKeys()` (`RETURNING <pk>`) |

Differences to keep in mind:
- `CommonMapper.upsert()` returns 1=insert/2=update on MySQL but always 1 on PostgreSQL (`save()` ignores it).
- `saveAll()` on PostgreSQL ignores only unique/PK conflicts; other errors (e.g. truncation) still throw. MySQL `INSERT IGNORE` swallows more. Intended.
- `save()` conflict target is the entity PK. A conflict on another unique key throws on PostgreSQL.
- Table names are quoted, so case is preserved — match `@XEntity` exactly (use lowercase tables). Columns are unquoted snake_case.
- Do not create NEW rows on a serial/identity PK by setting the id and calling `save()` — the sequence does not advance and later `insert()` collides.
- Custom `@Mapper` SQL runs as written: replace backticks, `LIMIT a, b`, `IFNULL`, `DATE_FORMAT`, `ON DUPLICATE KEY`, `@SelectKey("SELECT LAST_INSERT_ID()")`. Use `@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")` for generated keys. `CONCAT` works on both.
- JSON column mapped to a `String` field: PostgreSQL `jsonb` rejects `setString` → add `stringtype=unspecified` to the JDBC URL or use a jsonb TypeHandler.
- `timestamptz` → `OffsetDateTime`/`Instant`; `LocalDateTime` is for `timestamp`.
- Enums: use `varchar` + `CHECK` instead of native PostgreSQL enums (the default `EnumTypeHandler` binds strings).

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
- **`references/rest-client-and-auth.md`** — REST client strategy guide, @XRestService/@XRestAPI annotation reference, XWebClient API, header forwarding, error propagation, XErrorResponseHandler, common pitfalls, session/token authentication
- **`references/setup-and-config.md`** — Application setup, application.properties, mybatis-config.xml, Maven installation, i18n
- **`references/conventions-and-pitfalls.md`** — Javadoc conventions, common pitfalls, security warnings
