# YH REST Framework - AI Guide

## Project Structure

```
rest-framework/
  core/       → 공통 유틸, XPage, XPagination, NamingConvert 등
  rest-api/   → REST 컨트롤러 베이스, 예외 처리, XRestClient 프록시
  mybatis/    → MyBatis 기반 ORM 추상 레이어
  demo/       → 사용 예제
```

Build: `./gradlew build` (Gradle multi-module, Java 17, Spring Boot 3.3.1)

---

## MyBatis Module Architecture

```
사용자 코드                         프레임워크 내부
─────────                         ──────────────
@XRepository                      XRepositoryBeanScanner
UserRepository                         ↓
  extends IXRepository<K, T>      XRepositoryProxyFactoryBean
       ↓                               ↓
  (JDK Proxy)                    XRepositoryProxy (InvocationHandler)
       ↓                               ↓
                                  CommonMapper (@Mapper)
                                       ↓
                                  CrudSqlProvider (SQL 생성 + 캐시)
                                       ↓
                                  XResultInterceptor (결과 매핑, 페이지네이션)
```

### 역할 분리

- **CrudSqlProvider** → 순수 SQL 생성 (SELECT, FROM, WHERE)
- **XResultInterceptor** → pagination 전담 (COUNT, ORDER BY, LIMIT)

### Configuration 구조

- **XWebMvcConfiguration** → MVC 전담 (ArgumentResolver 등록)
- **XRestConfiguration** → HTTP 클라이언트 전담 (RestTemplate, 연결 풀)
- 두 클래스는 독립적인 `@Configuration`으로 상속 관계 없음

### REST Client Proxy URL 구성

- **Direct 모드** (`host` 지정): `host + @XRestAPI.value()` — serviceName은 논리적 식별자
- **Gateway 모드** (`host` 미지정): `gatewayHost + serviceName + version + @XRestAPI.value()` — 게이트웨이 라우팅용
- `XRestClientProxy.getOrCreateClient()`에서 모드별 XRestClient 생성자 분기

### 에러 전파 (REST Client Proxy → XExceptionHandler)

서버가 반환한 `ApiError`(code, message, description)가 프록시를 통해 그대로 전파됩니다.

```
서버 에러 발생 → XExceptionHandler → ApiError JSON 응답
  → XRestClient 수신 → ApiError 파싱 → XRestException throw
  → 호출측 XExceptionHandler catch → 동일 HTTP 상태코드 + ApiError 반환
```

- 상태코드 보존: 서버의 400/405/500 등이 프록시를 거쳐도 동일하게 전파
- `XExceptionHandler.getMessage()`는 `MessageSource.getMessage(code, args, code, locale)` 사용 — 이미 해석된 메시지 텍스트가 키로 재조회되어도 `NoSuchMessageException` 없이 원문 그대로 반환

---

## Annotations Reference

### Entity Mapping

| Annotation | Target | Description |
|---|---|---|
| `@XEntity(value, schema)` | Class | 테이블 매핑. `value`=테이블명, `schema`=스키마(optional) |
| `@XColumn(value, isPrimaryKey, isAutoIncrement, insert, update)` | Field | 컬럼 매핑. 미지정 시 camelCase→snake_case 자동 변환 |
| `@XIgnoreColumn` | Field | 매핑 제외 필드 |
| `@XDefaultValue(value, updateValue, isDBDefaultUsed, isDBValue)` | Field | INSERT/UPDATE 기본값 설정 |

### Repository

| Annotation | Target | Description |
|---|---|---|
| `@XRepository` | Interface | Repository 인터페이스 마커 |
| `@XRepositoryScan(basePackages)` | Class | Repository 스캔 범위 지정 (Application 클래스에 사용) |

### @XColumn 속성

```java
@XColumn(
    value = "column_name",      // 컬럼명 (default: camelCase→snake_case)
    isPrimaryKey = false,        // PK 여부
    isAutoIncrement = false,     // Auto Increment 여부
    insert = true,               // INSERT 포함 여부
    update = true                // UPDATE 포함 여부
)
```

### @XDefaultValue 속성 & 동작

```java
// INSERT 시 DB DEFAULT 사용 (컬럼 생략)
@XDefaultValue(isDBDefaultUsed = true)
private String region;

// INSERT 시 리터럴 값 사용
@XDefaultValue(value = "ACTIVE")
private String status;
// → INSERT: VALUES(..., 'ACTIVE', ...)

// INSERT 시 DB 표현식 사용
@XDefaultValue(value = "NOW()", isDBValue = true)
private LocalDateTime createdAt;
// → INSERT: VALUES(..., NOW(), ...)

// UPDATE 시 자동 값 설정
@XDefaultValue(updateValue = "NOW()", isDBValue = true)
private LocalDateTime updatedAt;
// → UPDATE: SET updated_at = NOW()
```

---

## Entity 정의 패턴

### 기본 엔티티

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

    @XIgnoreColumn
    private String tempField;  // DB 매핑 안됨
}
```

### 상속 지원

부모 클래스의 필드도 자동으로 매핑됩니다 (synthetic/static 필드 제외).

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
// → id, createdAt, updatedAt, name, status 모두 매핑됨
```

---

## Repository 정의 & 사용

### IXRepository 기본 메서드

```java
@XRepository
public interface UserRepository extends IXRepository<Long, User> {
    // 아래 메서드들은 IXRepository에서 자동 제공됨
}
```

| Method | Return | Description |
|---|---|---|
| `save(entity)` | `K` (PK) | PK null → INSERT, PK 존재 → Upsert (INSERT ... ON DUPLICATE KEY UPDATE) |
| `insert(entity)` | `K` (PK) | INSERT + auto-generated ID 반환 |
| `saveAll(List<T>)` | `K` | 배치 INSERT (INSERT IGNORE) |
| `update(entity)` | `int` | 전체 컬럼 UPDATE (null 포함) |
| `modify(entity)` | `int` | **Selective UPDATE** (null이 아닌 필드만) |
| `deleteById(key)` / `delete(key)` / `remove(key)` | `int` | PK 기반 DELETE |
| `deleteWhere(Map)` | `int` | 조건 기반 DELETE |
| `findOne(key)` | `T` | PK 기반 단건 조회 |
| `findAll()` | `List<T>` | 전체 조회 |
| `findAll(pagination)` | `XPage<T>` | 페이지네이션 조회 |
| `findWhere(Map)` | `List<T>` | 조건 기반 다건 조회 |
| `findWhere(pagination, Map)` | `XPage<T>` | 조건 + 페이지네이션 |
| `findOneWhere(Map)` | `T` | 조건 기반 단건 조회 |
| `exists(key)` | `boolean` | 존재 여부 |
| `count()` | `long` | 전체 건수 |
| `count(Map)` | `long` | 조건 기반 건수 |

### save() 동작 (Upsert)

```java
// PK가 null → INSERT (auto-increment ID 자동 설정)
user.setId(null);
userRepository.save(user);

// PK가 존재 → INSERT ... ON DUPLICATE KEY UPDATE (원자적)
// DB에 해당 PK가 없으면 INSERT, 있으면 UPDATE
user.setId(1L);
userRepository.save(user);
```

### update vs modify

```java
// update: 모든 updatable 컬럼 SET (null도 포함)
// → UPDATE users SET name='홍길동', email=NULL, status=NULL WHERE id=1
repository.update(user);

// modify: null이 아닌 필드만 SET (Selective Update)
// → UPDATE users SET name='홍길동' WHERE id=1  (email, status 유지)
repository.modify(user);
```

---

## Query Derivation (메서드명 기반 쿼리 생성)

Repository 인터페이스에 메서드를 선언하면, 메서드명을 파싱하여 자동으로 SQL을 생성합니다.

### 지원 prefix

| Prefix | 반환 타입 | SQL |
|---|---|---|
| `findBy` | `T` 또는 `List<T>` (반환타입에 따라 자동) | SELECT ... WHERE ... |
| `findAllBy` | `List<T>` | SELECT ... WHERE ... |
| `countBy` | `long` | SELECT COUNT(*) WHERE ... |
| `existsBy` | `boolean` | SELECT COUNT(*) WHERE ... > 0 |
| `deleteBy` | `int` | DELETE FROM ... WHERE ... |

### And 조합

```java
@XRepository
public interface PartnerRepository extends IXRepository<Long, Partner> {
    Partner findByEmail(String email);
    List<Partner> findByStatus(String status);
    Partner findByPartnerIdAndPartnerUserIdAndStatus(
        Long partnerId, Long partnerUserId, String status
    );
    long countByStatus(String status);
    boolean existsByEmail(String email);
    int deleteByPartnerIdAndStatus(Long partnerId, String status);
}
```

---

## Caching Architecture

3단계 캐싱으로 반복 호출 최적화:

```
Layer 1: XRepositoryProxy.methodFieldCache      → 메서드명 파싱 결과 캐시
Layer 2: CrudSqlProvider.SQL_CACHE               → SQL 템플릿 캐시 (ORDER BY 제외)
Layer 3: EntityMetadataFactory.entityMetadataCache → 엔티티 메타데이터 캐시
```

---

## Pagination

```java
XPagination pagination = new XPagination();
pagination.setPage(1);
pagination.setSize(20);
pagination.addOrder(new XOrder("createdAt", XDirection.DESC));

XPage<User> result = userRepository.findAll(pagination);
// result.getTotalCount()  → 전체 건수
// result.getPageRows()    → 현재 페이지 데이터
// result.getPage()        → 현재 페이지 번호
```

---

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

### Configuration Properties

```properties
# HTTP Client
x.rest.client.pool-size=200
x.rest.client.connection-request-timeout=30
x.rest.client.response-timeout=30

# Session (optional)
x.rest.session.secret-key=your-hmac-secret
x.rest.session.expire-days=7
```

---

## Key Source Files

| File | Role |
|---|---|
| `core/.../data/XPage.java` | 페이지네이션 결과 컨테이너 |
| `core/.../data/XPagination.java` | 페이지네이션 요청 모델 |
| `rest-api/.../configuration/XRestConfiguration.java` | HTTP 클라이언트 설정 (독립 @Configuration) |
| `rest-api/.../configuration/XWebMvcConfiguration.java` | MVC 리졸버 설정 (독립 @Configuration) |
| `rest-api/.../handler/XExceptionHandler.java` | 글로벌 예외 처리 + i18n |
| `rest-api/.../proxy/XRestClient.java` | REST 클라이언트 (RestTemplate 래퍼) |
| `rest-api/.../proxy/XRestClientProxy.java` | REST 클라이언트 JDK Proxy (Direct/Gateway 모드 분기) |
| `mybatis/.../repository/IXRepository.java` | Repository 인터페이스 (CRUD 시그니처) |
| `mybatis/.../proxy/XRepositoryProxy.java` | JDK Proxy, 메서드 라우팅 & Upsert |
| `mybatis/.../provider/CrudSqlProvider.java` | SQL 생성 & 캐싱 |
| `mybatis/.../plugin/XResultInterceptor.java` | MyBatis 인터셉터 (ResultMap, 페이지네이션) |
| `mybatis/.../meta/EntityMetadataFactory.java` | 어노테이션 파싱 & 메타데이터 캐시 |
