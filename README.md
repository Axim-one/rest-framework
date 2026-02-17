# Axim REST Framework

Spring Boot + MyBatis 기반의 경량 REST 프레임워크. 어노테이션 기반 엔티티 매핑과 Repository 패턴으로 MyBatis의 SQL 제어력을 유지하면서 보일러플레이트를 최소화합니다.

## Features

- **MyBatis Repository Proxy** - `IXRepository<K, T>` 인터페이스 선언만으로 CRUD 자동 생성
- **Query Derivation** - `findByEmailAndStatus()`처럼 메서드명에서 SQL 자동 파싱
- **Annotation-based Mapping** - `@XEntity`, `@XColumn`, `@XDefaultValue`로 테이블/컬럼 매핑
- **Atomic Upsert** - `save()` 호출 시 `INSERT ... ON DUPLICATE KEY UPDATE`로 원자적 처리
- **Selective Update** - `modify()`로 null이 아닌 필드만 UPDATE
- **Pagination** - `XPagination` + `XPage<T>`로 페이지네이션, COUNT, 정렬 자동 처리
- **REST Client Proxy** - `@XRestService` 선언형 HTTP 클라이언트 (OpenFeign 유사)
- **Exception Handling** - `@RestControllerAdvice` 기반 글로벌 예외 처리 + i18n 메시지
- **Session/Token** - HMAC-SHA256 서명 기반 Access Token 관리

## Module Structure

```
rest-framework/
├── core/       공통 모델 (XPage, XPagination, NamingConvert)
├── rest-api/   REST 컨트롤러, 예외 처리, HTTP 클라이언트
├── mybatis/    MyBatis Repository 프록시, SQL 생성, 인터셉터
└── demo/       사용 예제
```

## Requirements

- Java 17+
- Spring Boot 3.3+
- MySQL 5.7+ / 8.0+
- MyBatis 3.0+

## Installation

[![](https://jitpack.io/v/Axim-one/rest-framework.svg)](https://jitpack.io/#Axim-one/rest-framework)

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

> 필요한 모듈만 선택적으로 추가할 수 있습니다. `rest-api`는 `core`에 의존하므로, `rest-api`만 추가해도 `core`가 함께 포함됩니다.

## Quick Start

### 1. Entity 정의

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
}
```

### 2. Repository 선언

```java
@XRepository
public interface UserRepository extends IXRepository<Long, User> {
    User findByEmail(String email);
    List<User> findByStatus(String status);
    boolean existsByEmail(String email);
    long countByStatus(String status);
}
```

### 3. Service에서 사용

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User create(User user) {
        userRepository.save(user);  // INSERT, auto-increment ID 자동 설정
        return user;
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

### 4. Application 설정

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

## Repository API

| Method | Return | Description |
|---|---|---|
| `save(entity)` | `K` | PK null -> INSERT, PK 존재 -> Upsert |
| `insert(entity)` | `K` | INSERT + auto-generated ID |
| `saveAll(List<T>)` | `K` | Batch INSERT (INSERT IGNORE) |
| `update(entity)` | `int` | 전체 컬럼 UPDATE |
| `modify(entity)` | `int` | Selective UPDATE (null 제외) |
| `findOne(key)` | `T` | PK 단건 조회 |
| `findAll()` | `List<T>` | 전체 조회 |
| `findAll(pagination)` | `XPage<T>` | 페이지네이션 조회 |
| `findWhere(Map)` | `List<T>` | 조건 조회 |
| `exists(key)` | `boolean` | 존재 여부 |
| `count()` / `count(Map)` | `long` | 건수 |
| `deleteById(key)` | `int` | PK 삭제 |
| `deleteWhere(Map)` | `int` | 조건 삭제 |

## Query Derivation

메서드명을 선언하면 SQL이 자동 생성됩니다.

```java
@XRepository
public interface OrderRepository extends IXRepository<Long, Order> {
    Order findByOrderNo(String orderNo);                    // WHERE order_no = ?
    List<Order> findByUserIdAndStatus(Long userId, String status);  // WHERE user_id = ? AND status = ?
    long countByStatus(String status);                      // SELECT COUNT(*) WHERE status = ?
    boolean existsByOrderNo(String orderNo);                // SELECT COUNT(*) WHERE order_no = ? > 0
    int deleteByUserIdAndStatus(Long userId, String status); // DELETE WHERE user_id = ? AND status = ?
}
```

**지원 Prefix**: `findBy`, `findAllBy`, `countBy`, `existsBy`, `deleteBy`
**조건 조합**: `And`로 연결 (예: `findByNameAndEmail`)

## Annotations

| Annotation | Target | Description |
|---|---|---|
| `@XEntity(value, schema)` | Class | 테이블 매핑 |
| `@XColumn(value, isPrimaryKey, isAutoIncrement, insert, update)` | Field | 컬럼 매핑 |
| `@XDefaultValue(value, updateValue, isDBDefaultUsed, isDBValue)` | Field | INSERT/UPDATE 기본값 |
| `@XIgnoreColumn` | Field | 매핑 제외 |
| `@XRepository` | Interface | Repository 마커 |
| `@XRepositoryScan` | Class | Repository 스캔 범위 |

## Configuration

```properties
# HTTP Client (rest-api module)
axim.rest.client.pool-size=200
axim.rest.client.connection-request-timeout=30
axim.rest.client.response-timeout=30

# Session (rest-api module, optional)
axim.rest.session.secret-key=your-hmac-secret
axim.rest.session.expire-days=7
```

## Build

```bash
./gradlew build
```

## Documentation

- [Framework Usage Guide](framework-guide.md) - 상세 사용법

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

## License

Private
