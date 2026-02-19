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
    implementation 'com.github.Axim-one.rest-framework:core:1.0.2'
    implementation 'com.github.Axim-one.rest-framework:rest-api:1.0.2'
    implementation 'com.github.Axim-one.rest-framework:mybatis:1.0.2'
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
        <version>1.0.2</version>
    </dependency>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>rest-api</artifactId>
        <version>1.0.2</version>
    </dependency>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>mybatis</artifactId>
        <version>1.0.2</version>
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

## Exception Handling

```java
// Bad request (400)
throw new InvalidRequestParameterException("invalid.email.format");

// Unauthorized (401)
throw new UnAuthorizedException();

// Not found (404)
throw new NotFoundException("user.not.found");
```

Error response format:

```json
{
    "code": 400,
    "message": "Invalid request parameter.",
    "description": "Email format is invalid.",
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

## Custom Mapper for Complex Queries

For queries beyond what query derivation supports, use standard MyBatis `@Mapper`:

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
| `save(entity)` | `K` | Upsert: PK null=INSERT, PK present=INSERT ON DUPLICATE KEY UPDATE |
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
| `count()` | `long` | Total row count |
| `count(Map)` | `long` | Conditional row count |
| `deleteById(key)` | `int` | Delete by primary key |
| `deleteWhere(Map)` | `int` | Delete by conditions |
