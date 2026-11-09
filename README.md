# Axim REST Framework

Spring Boot + MyBatis 기반의 경량 REST 프레임워크. 어노테이션 기반 엔티티 매핑과 Repository 패턴으로 MyBatis의 SQL 제어력을 유지하면서 보일러플레이트를 최소화합니다.

## Features

- **MyBatis Repository Proxy** - `IXRepository<K, T>` 인터페이스 선언만으로 CRUD 자동 생성
- **Query Derivation** - `findByEmailAndStatus()`처럼 메서드명에서 SQL 자동 파싱
- **Annotation-based Mapping** - `@XEntity`, `@XColumn`, `@XDefaultValue`로 테이블/컬럼 매핑
- **Composite Primary Key** - 복합키 엔티티 지원 (키 클래스 기반 CRUD)
- **Atomic Upsert** - `save()` 호출 시 `INSERT ... ON DUPLICATE KEY UPDATE`(PostgreSQL 은 `ON CONFLICT`)로 원자적 처리
- **MySQL / PostgreSQL** - `axim.mybatis.dialect` 또는 DataSource 자동 감지 (v1.5.0)
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
- MySQL 5.7+ / 8.0+ 또는 PostgreSQL 9.5+ (통합 테스트: MySQL 8.0, PostgreSQL 16)
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
    implementation 'com.github.Axim-one.rest-framework:core:1.5.0'
    implementation 'com.github.Axim-one.rest-framework:rest-api:1.5.0'
    implementation 'com.github.Axim-one.rest-framework:mybatis:1.5.0'
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
        <version>1.5.0</version>
    </dependency>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>rest-api</artifactId>
        <version>1.5.0</version>
    </dependency>
    <dependency>
        <groupId>com.github.Axim-one.rest-framework</groupId>
        <artifactId>mybatis</artifactId>
        <version>1.5.0</version>
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
| `save(entity)` | `K` | PK null → INSERT, PK 존재 → Upsert (복합키: 모든 PK non-null → upsert) |
| `insert(entity)` | `K` | INSERT + auto-generated ID (복합키: 키 클래스 반환) |
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

## Composite Primary Key

복합키 엔티티는 여러 `@XColumn(isPrimaryKey = true)` 필드를 선언하고, 키 클래스를 `IXRepository<K, T>`의 `K`로 사용합니다.

```java
// Key class — 필드명은 엔티티의 PK 필드명과 동일해야 합니다
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
// 사용 예제
OrderItemKey key = new OrderItemKey();
key.setOrderId(1L);
key.setItemId(100L);

repository.findOne(key);       // WHERE order_id = ? AND item_id = ?
repository.delete(key);        // WHERE order_id = ? AND item_id = ?
repository.save(orderItem);    // 모든 PK set → upsert, any null → insert
repository.insert(orderItem);  // OrderItemKey 반환 (both PK values)
```

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

# MyBatis dialect (optional) — mysql | postgresql, 미지정 시 DataSource 자동 감지
axim.mybatis.dialect=postgresql
```

## PostgreSQL (v1.5.0)

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

## Build

```bash
./gradlew build
```

## Documentation

- [Framework Usage Guide](docs/guide.md) - 상세 사용법 (English)

### AI 도구에서 Context7으로 사용하기

이 프레임워크는 [Context7](https://context7.com)에 등록되어 있어, AI 코딩 도구(Claude Code, Cursor 등)에서 최신 API 문서를 자동으로 참조할 수 있습니다.

**Claude Code** — MCP 서버 설정 (`~/.claude/settings.json`):

```json
{
  "mcpServers": {
    "context7": {
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp@latest"]
    }
  }
}
```

**Cursor** — MCP 서버 설정 (`.cursor/mcp.json`):

```json
{
  "mcpServers": {
    "context7": {
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp@latest"]
    }
  }
}
```

설정 후 프롬프트에 `use context7`을 추가하면 AI가 자동으로 최신 문서를 참조합니다:

```
UserRepository에서 페이지네이션 조회를 구현해줘. use context7
```

또는 라이브러리 ID를 직접 지정할 수도 있습니다:

```
/axim-one/rest-framework 문서를 참고해서 @XEntity와 @XRepository 설정 예제를 보여줘. use context7
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

## License

Private
