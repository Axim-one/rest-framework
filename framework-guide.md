# Framework Usage Guide

This guide provides an overview of how to use the YH REST Framework, including MyBatis integration, REST client proxy, and Spring application configuration.

## 1. MyBatis Framework Usage

### 1.1. Entity 정의

`@XEntity`, `@XColumn`, `@XDefaultValue`, `@XIgnoreColumn` 어노테이션으로 엔티티를 정의합니다.

```java
@Data
@XEntity("users")
public class User {

    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;

    @XColumn("email")
    private String email;

    private String name;  // @XColumn 없으면 camelCase→snake_case 자동 변환

    @XDefaultValue(value = "NOW()", isDBValue = true)
    private LocalDateTime createdAt;

    @XDefaultValue(updateValue = "NOW()", isDBValue = true)
    private LocalDateTime updatedAt;

    @XColumn(insert = false, update = false)  // INSERT/UPDATE 제외
    private String readOnlyField;

    @XIgnoreColumn  // DB 매핑 안됨
    private String transientField;
}
```

**상속 지원**: 부모 클래스의 필드도 자동 매핑됩니다.

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

### 1.2. Repository 정의

`IXRepository<K, T>`를 확장하고 `@XRepository`를 붙이면 구현 없이 CRUD가 제공됩니다.

```java
@XRepository
public interface UserRepository extends IXRepository<Long, User> {
    // 파생 쿼리 (메서드명 자동 파싱)
    User findByEmail(String email);
    List<User> findByStatus(String status);
    boolean existsByEmail(String email);
    long countByStatus(String status);
    int deleteByStatusAndName(String status, String name);
}
```

### 1.3. CRUD 사용

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    // INSERT (auto-increment ID 자동 설정)
    public User create(User user) {
        userRepository.save(user);  // user.getId()에 생성된 ID가 설정됨
        return user;
    }

    // Upsert (PK가 있으면 INSERT ... ON DUPLICATE KEY UPDATE)
    public User upsert(User user) {
        user.setId(1L);
        userRepository.save(user);  // 원자적 upsert
        return user;
    }

    // Selective Update (null 필드 무시)
    public User partialUpdate(Long id, UserUpdateRequest req) {
        User user = new User();
        user.setId(id);
        user.setName(req.getName());  // 변경할 필드만 set
        userRepository.modify(user);  // name만 UPDATE
        return userRepository.findOne(id);
    }

    // 조건 조회
    public List<User> findActive() {
        return userRepository.findWhere(Map.of("status", "ACTIVE"));
    }

    // 페이지네이션
    public XPage<User> list(int page, int size) {
        XPagination pagination = new XPagination();
        pagination.setPage(page);
        pagination.setSize(size);
        pagination.addOrder(new XOrder("createdAt", XDirection.DESC));
        return userRepository.findAll(pagination);
    }
}
```

### 1.4. Custom Query (복잡한 쿼리는 별도 Mapper)

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

---

## 2. REST API Framework Usage

### 2.1. REST Controller

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

    // XPagination 자동 바인딩: ?page=1&size=10&sort=email,asc
    @GetMapping
    public XPage<User> searchUsers(@XPaginationDefault XPagination pagination,
                                   @RequestParam(value = "keyword", required = false) String keyword) {
        return userService.search(pagination, keyword);
    }
}
```

### 2.2. Base Controller

`XAbstractController`를 상속하면 요청 파라미터 유틸리티를 사용할 수 있습니다.

```java
@RestController
@RequestMapping("/api/v1/products")
public class ProductController extends XAbstractController {

    @GetMapping
    public List<Product> list() {
        String category = getParameter("category");       // 필수 파라미터
        String brand = getOptionParameter("brand");        // 선택 파라미터
        int page = getIntParameter("page", 1);             // int + 기본값
        // ...
    }
}
```

### 2.3. Session Management

`XSessionController<T>`를 상속하면 세션 관리가 가능합니다.

```java
@RestController
@RequestMapping("/api/v1/my")
public class MyController extends XSessionController<MySessionData> {

    @GetMapping("/profile")
    public MySessionData getProfile() {
        MySessionData session = getSession();  // 토큰 파싱 + 유효성 검증
        return session;
    }
}
```

---

## 3. Exception Handling

### 3.1. 예외 던지기

```java
// 파라미터 오류 (400)
throw new InvalidRequestParameterException("invalid.email.format");

// 인증 실패 (401)
throw new UnAuthorizedException();

// 리소스 없음 (404)
throw new NotFoundException("user.not.found");
```

### 3.2. 에러 응답 형식

```json
{
    "code": 400,
    "message": "Invalid request parameter.",
    "description": "Email format is invalid.",
    "data": null
}
```

### 3.3. 메시지 정의

`src/main/resources/messages.yml`:

```yaml
server:
  http:
    error:
      invalid-parameter: "Invalid request parameter."
      notfound-api: "API endpoint not found."
      server-error: "An unexpected server error occurred."
```

---

## 4. XRestClient (서비스 간 통신)

### 4.1. 선언형 REST 클라이언트 (Proxy 방식)

```java
@XRestService(name = "user-service", host = "${external.user-service.host}", version = "v1")
public interface UserServiceClient {

    @XRestAPI(url = "/users/{id}", method = XHttpMethod.GET)
    User getUser(@PathVariable("id") Long id);

    @XRestAPI(url = "/users", method = XHttpMethod.POST)
    User createUser(@RequestBody User user);
}
```

`@ComponentScan`에 `one.axim.framework.rest`를 포함하고 `@XRestServiceScan`으로 스캔하면 자동으로 프록시가 생성됩니다.

### 4.2. 직접 사용

```java
@Service
public class ExternalService {

    private final XRestClient client;

    public ExternalService(RestTemplate restTemplate) {
        this.client = new XRestClient("https://api.example.com", "external-api", "v1", restTemplate);
    }

    public User getUser(Long id) throws XRestException, IOException {
        return client.get("/users/{id}", User.class, null,
                Map.of("id", String.valueOf(id)), null);
    }
}
```

---

## 5. Application Setup

### 5.1. Application 클래스

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

### 5.2. application.properties

```properties
spring.application.name=my-app
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

# MyBatis dialect (optional) — mysql | postgresql, 미지정 시 DataSource 자동 감지 (5.5 참고)
# axim.mybatis.dialect=postgresql
```

### 5.3. mybatis-config.xml

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

### 5.4. build.gradle (사용자 앱)

```groovy
dependencies {
    implementation project(':core')
    implementation project(':rest-api')
    implementation project(':mybatis')
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

### 5.5. PostgreSQL (v1.5.0)

기본 방언은 MySQL 이며, MySQL 이 받는 SQL 은 1.4.x 와 **문자열 단위로 같습니다**(스냅샷 테스트로 고정). PostgreSQL 에서는 같은 API 그대로 쓰고 방언만 지정합니다.

```yaml
axim:
  mybatis:
    dialect: postgresql   # mysql | postgresql
```

- 설정하지 않으면 DataSource 의 `DatabaseMetaData.getDatabaseProductName()` 으로 **자동 감지**합니다. 기동 시 DB 에 붙지 못하면 첫 쿼리에서 다시 감지하고, 그래도 모르면 mysql 로 둡니다.
- 잘못된 값(`postgre` 등)은 기동 시 예외로 막습니다.
- PostgreSQL JDBC 드라이버(`org.postgresql:postgresql`)는 앱에서 추가하세요. 프레임워크는 `mysql-connector-j` 만 `runtimeOnly` 로 가져옵니다.
- `mybatis-config.xml` 의 `XResultInterceptor` 플러그인 등록은 PostgreSQL 에서도 필수입니다(생성 키 회수를 여기서 처리).

| 항목 | MySQL | PostgreSQL |
|---|---|---|
| 테이블 식별자 | `` `schema`.`table` `` | `"schema"."table"` |
| 페이지네이션 | `LIMIT offset, size` | `LIMIT size OFFSET offset` |
| `save()` (PK 가 다 채워짐) | `INSERT ... ON DUPLICATE KEY UPDATE` | `INSERT ... ON CONFLICT (<PK 컬럼>) DO UPDATE SET ...` |
| `saveAll()` | `INSERT IGNORE` | `INSERT ... ON CONFLICT DO NOTHING` |
| `insert()` 자동 ID 회수 | `SELECT LAST_INSERT_ID()` | JDBC `getGeneratedKeys()` (`RETURNING <pk>`) |

**동작 차이 / 제약**

- **한 앱 안에서 MySQL·PostgreSQL 혼용은 지원하지 않습니다.** 방언은 JVM 전역 하나입니다.
- **upsert 반환값**: MySQL 은 insert=1 / update=2, PostgreSQL 은 항상 1. `save()` 는 이 값을 쓰지 않으므로 영향이 없지만, `CommonMapper.upsert()` 를 직접 호출해 값으로 분기하는 코드는 PostgreSQL 에서 달라집니다.
- **`saveAll()` 중복 무시 범위**: MySQL `INSERT IGNORE` 는 충돌 외 오류(값 잘림 등)도 경고로 삼키지만, PostgreSQL `ON CONFLICT DO NOTHING` 은 unique/PK 충돌만 무시하고 나머지는 예외를 던집니다. 의도된 차이입니다.
- **`save()` 의 충돌 대상은 엔티티 PK** 입니다. PK 외 unique 키 충돌은 갱신이 아니라 예외가 됩니다(MySQL `ON DUPLICATE KEY` 는 아무 unique 키에나 걸림).
- **테이블명은 따옴표로 감싸므로 대소문자가 그대로** 나갑니다. `@XEntity` 값과 실제 테이블명을 정확히(보통 소문자로) 맞추세요. 컬럼명은 따옴표 없는 snake_case 라 그대로 맞습니다.
- 자동증가(`serial`/`identity`) PK 에 `save()` 로 id 를 직접 넣어 **새 행**을 만들면 시퀀스가 움직이지 않아 이후 `insert()` 가 PK 충돌을 냅니다. 신규 행은 id 를 비우고 `insert()`/`save()` 하세요.
- 커스텀 `@Mapper` SQL 은 그대로 실행됩니다. 백틱, `LIMIT a, b`, `IFNULL`, `DATE_FORMAT`, `ON DUPLICATE KEY`, `@SelectKey("SELECT LAST_INSERT_ID()")` 같은 MySQL 전용 문법은 직접 바꾸세요(`CONCAT` 은 공통). 커스텀 매퍼의 생성 키는 `@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")`.

**소비자 쪽 주의 (프레임워크 변경 아님)**

- JSON 컬럼을 `String` 필드로 매핑하면 PostgreSQL `jsonb` 가 `setString` 을 거부합니다 → JDBC URL 에 `stringtype=unspecified` 를 붙이거나 jsonb `TypeHandler` 를 쓰세요.
- `timestamptz` 는 `OffsetDateTime`/`Instant` 로, `LocalDateTime` 은 `timestamp` 용입니다.
- enum 은 PostgreSQL 네이티브 enum 대신 `varchar` + `CHECK` 로 두세요(기본 `EnumTypeHandler` 가 문자열로 넣습니다).
