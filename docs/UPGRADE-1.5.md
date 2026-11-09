# 1.5.0 업그레이드 가이드

1.3.x · 1.4.x 를 쓰는 서비스를 1.5.0 으로 올릴 때 확인할 것과 고칠 것.
버전별 변경을 **"반드시 고쳐야 컴파일되는 것"** 과 **"동작이 바뀌는 것"** 으로 나눴고,
각 항목에 해당 여부를 확인하는 `grep` 을 붙였다. 해당이 없으면 의존성 버전만 올리면 된다.

## 0. 현재 소비자 점검 결과 (2026-11-10)

각 서비스의 커밋된 코드를 복사해 의존성만 1.5.0(로컬 빌드)으로 바꾸고 `compileJava compileTestJava test` 를 돌렸다.
원본 저장소는 건드리지 않았다.

| 서비스 | 현재 | 컴파일 | 테스트 (1.5.0 / 기존) | 필요한 코드 수정 |
|---|---|---|---|---|
| cryptoments-backend (`origin/main` 49c1ccb8) | 1.4.2 | 전 모듈 통과 | `:common` 136/136, `:core` 1154/1155 (실패 1건은 1.4.2 에서도 동일) | 없음 |
| TORQ laas-chain/main-server (`main` 9b457fe) | 1.3.2 | 통과 | 791/791 | 없음 — 의존성 좌표만 변경(아래) |
| talk-server (`main` 2f5b1e6) | 1.3.2 | 통과 | 24/24 | 없음 |
| blockchain-monitor-system (`refactor/scan-engine` 86db11d) | 1.3.0 | 통과 | 61/61 (skip 3, 기존과 동일) | 없음 |

아래 체크리스트의 grep 도 네 서비스 모두 해당 없음이었다. 정적 분석으로 확인한 `save()` 동작 변경(§2-1) 영향도 없었다.
cryptoments `:core:test` 의 eKYC 테스트 1건 실패(`IdAnalyzerEkycGatewayTest`)는 1.4.2 에서도 동일하게 실패하는 기존 문제다.

**TORQ 의존성 좌표**: 지금은 루트 좌표 `com.github.axim-one:rest-framework:1.3.2` 하나를 쓴다. 모듈 좌표로 바꾼다.

```gradle
implementation 'com.github.Axim-one.rest-framework:core:1.5.0'
implementation 'com.github.Axim-one.rest-framework:rest-api:1.5.0'
implementation 'com.github.Axim-one.rest-framework:mybatis:1.5.0'
```

---

## 1. 1.3.x → 1.4.x 변경 (1.3.x 에서 올리는 경우에만)

### 컴파일이 깨지는 것

| 변경 | 확인 | 조치 |
|---|---|---|
| `IXRepository.saveAll()` 반환 `K` → `int` (실제 삽입 행 수) | `grep -rnE '=\s*\w+\.saveAll\(' --include=*.java src` | 반환값을 PK 로 쓰던 코드는 삽입 행 수로 해석을 바꾼다 |
| `XRestEnvironment.getInstance()` 제거 (정적 싱글톤 → Spring 주입) | `grep -rn 'XRestEnvironment.getInstance' --include=*.java .` | `XRestEnvironment` 를 주입받거나 `@Value` 로 읽는다 |
| `SessionData.isExpire()` → `isExpire(int expireDays)` | `grep -rnE 'isExpire\(\s*\)' --include=*.java .` | `isExpire(handler.getTokenExpireDays())` 처럼 일수를 넘긴다 |

### 동작이 바뀌는 것

| 변경 | 영향 받는 경우 | 확인 / 조치 |
|---|---|---|
| `@XRestService` host 에 스킴이 없으면 `https://` (기존 `http://`) | `host = "api.internal:8080"` 처럼 스킴 없이 쓰고 평문 HTTP 인 서비스 | `grep -rn '@XRestService(' --include=*.java .` 와 `${...}` 로 참조하는 설정값을 확인. 평문이면 `http://` 를 명시 |
| 기동 검증: `SessionData` 파라미터를 받는 컨트롤러가 있는데 `XAccessTokenParseHandler` 빈이 없으면 **기동 실패** | 인증 핸들러 없이 `SessionData` 를 받던 서비스 (그동안 인증 없이 처리되고 있었다) | `XBaseAccessTokenHandler` 를 상속한 `@Component` 를 등록 |
| `axim.rest.session.secret-key` 미설정 시 WARN 로그 | 서명 없는 토큰을 쓰던 서비스 | 운영에서는 반드시 설정 |
| 요청 로그의 민감 파라미터(`password`, `token`, `api-key` 등) 마스킹 | 로그 파싱에 의존하는 곳 | 없음 |
| `XPage` 페이지네이션: 명시 정렬이 있으면 호출자 `@Select` 의 최상위 `ORDER BY` 를 대체 | `@Select` 에 `ORDER BY` + `XPagination` 정렬을 함께 쓰던 매퍼 (기존엔 SQL 문법 오류) | 없음 (버그 수정) |
| `NamingConvert.toCamelCase("a__b")` → `aB` | 연속 언더스코어 컬럼 | `grep -rn 'NamingConvert.toCamelCase' --include=*.java .` |

> 참고: 문서(README·framework-guide)에 `axim.rest.session.expire-days` 로 적혀 있었지만 코드가 읽는 키는 처음부터
> **`axim.rest.session.token-expire-days`** 다. `expire-days` 로 설정했다면 적용되지 않고 기본 90일이었다.
> (1.5.0 에서 문서 정정)

---

## 2. 1.4.x → 1.5.0 변경

컴파일이 깨지는 변경은 없다. 공개 API 시그니처는 그대로이고, 추가만 있다
(`XPaginationSql.limit(XPagination, XDialect)`, `EntityMetadata` 새 생성자, `one.axim.framework.mybatis.dialect` 패키지).

### 동작이 바뀌는 것

**2-1. `save()` 에 자동증가 PK 값이 있으면 그 행을 갱신한다 (MySQL 버그 수정)**

1.4.x 는 upsert INSERT 에서 자동증가 PK 를 뺐다. 그래서 id 를 채운 엔티티를 `save()` 하면 DB 가 새 PK 를 만들었고,
PK 외 unique 키가 없는 테이블에서는 **갱신 대신 새 행이 생겼다**(반환값은 옛 id). 1.5.0 은 PK 로 충돌시켜 그 행을 갱신한다.

- 영향 받는 경우: 자동증가 PK 엔티티를 `findOne`/`findBy*` 로 읽거나 `setId(...)` 한 뒤 `save()` 하는 코드.
  PK 외 unique 키가 있는 테이블은 1.4.x 에서도 그 키로 갱신됐으므로 결과가 같다.
- 확인: `save(` 호출 중 인자가 조회 결과이거나 `setId` 된 엔티티인 곳. 새로 `builder()`/`new` 한 엔티티(id null)는 insert 경로라 무관.
- 조치: 대부분 그대로 두면 된다(의도대로 동작하게 됨). 1.4.x 에서 이 경로로 **중복 행이 이미 쌓였을 수 있으니** 해당 테이블은 데이터를 확인한다.
- SQL 차이: 자동증가 PK 엔티티의 upsert 에 `<if test="model.id != null">id,</if>` 두 줄 추가. 그 외 MySQL SQL 은 1.4.2 와 같다(`CrudSqlSnapshotTest` 가 고정).

**2-2. `XWebClientFactory.create()` 가 호출마다 새 `XWebClient` 를 돌려준다**

1.4.x 는 baseUrl 별 인스턴스 하나를 공유했고 `errorHandler(...)` 가 그 인스턴스를 바꿨다.
그래서 한 곳에서 `create(url).errorHandler(h)` 를 하면 같은 baseUrl 을 쓰는 다른 모든 곳에도 `h` 가 적용됐다.

- 영향 받는 경우: **한 곳에서 핸들러를 걸어 두고, 다른 곳에서 `create(url)` 로 받은 클라이언트가 그 핸들러를 쓰길 기대하는** 코드.
- 확인: `grep -rn '\.errorHandler(' --include=*.java .`
- 조치: 핸들러를 건 인스턴스를 필드에 보관해 쓰거나, 쓰는 곳마다 `errorHandler(...)` 를 건다. 호출마다 `create()` 해도 비용은 1.4.x 와 같다(baseUrl 이 적용된 RestClient 는 캐시).

**2-3. gateway 모드인데 `axim.rest.gateway.host` 가 없으면 `IllegalStateException`**

`host` 없는 `@XRestService` 는 gateway 모드다. 1.4.x 는 설정이 없을 때 기본값이 문자열 `"@null"` 이라 `https://@null/...` 로 요청했다(연결 실패).
1.5.0 은 첫 호출에서 원인을 적은 예외를 던진다. 정상 설정된 서비스는 영향 없음.

**2-4. `XWebClient` 연결 실패 예외의 `remoteServiceName` = baseUrl**

`UnavailableServerException.getRemoteServiceName()` 에 baseUrl 이 들어간다(`@JsonIgnore` — API 응답에 안 나감). `description` 은 1.4.x 와 같이 비어 있다.

**2-5. PostgreSQL 방언 (MySQL 서비스는 영향 없음)**

- `XDialectConfig` 가 `@ComponentScan("one.axim.framework.mybatis")` 로 등록된다. `axim.mybatis.dialect` 가 없으면 기동 중 DB 에 붙지 않고,
  첫 쿼리에서 커넥션 메타데이터로 감지한다. 모르는 DB(H2 테스트 등)는 MySQL 로 동작한다.
- PostgreSQL 로 옮기는 서비스는 README 「PostgreSQL」 절을 따른다.

### 문서만 바뀐 것 (코드 동작은 그대로)

- **날짜**: "JSON 날짜는 `yyyy-MM-dd HH:mm:ss`" 라는 문서상의 규칙을 삭제했다. 실제로는 `@XRestService` **응답** DTO 의 `java.util.Date` 만
  Spring Boot 서버의 ISO-8601 을 읽지 못한다(`InvalidFormatException`). 서비스 간 DTO 날짜는 `java.time.*` 를 쓴다.
  확인: `@XRestService` 반환 타입 DTO 에 `java.util.Date` 필드가 있는지.
- **`@RequestParam`**: GET/DELETE 는 query string, POST/PUT/PATCH(`@RequestBody` 없음)는 form 바디, `@RequestBody` 가 있으면 무시된다.
  `@RequestBody Map` 은 JSON 이다. (이전 문서는 반대로 적혀 있었다) — `WireContractTest` 가 고정.

---

## 3. 올리는 순서

1. 의존성 버전을 1.5.0 으로 바꾼다 (TORQ 는 모듈 좌표로).
2. 위 grep 을 돌려 해당 항목만 고친다.
3. `./gradlew test` 와 스테이징 기동 확인 — 특히 1.3.x 에서 올리는 서비스는 기동 검증(§1)과 `https` 기본값(§1)을 스테이징에서 본다.
4. `save()` 로 기존 행을 갱신하던 테이블이 있으면(§2-1) 1.4.x 시절 중복 행이 있는지 데이터를 확인한다.
