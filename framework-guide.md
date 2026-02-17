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
x.rest.client.pool-size=200
x.rest.client.connection-request-timeout=30
x.rest.client.response-timeout=30

# Framework Session (optional)
x.rest.session.secret-key=your-hmac-secret-key
x.rest.session.expire-days=7
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
