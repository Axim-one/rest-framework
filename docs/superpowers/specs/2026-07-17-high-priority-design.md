# TODO High Priority 일괄 처리 — 설계

작성일: 2026-07-17
브랜치: `Rayn-Kim/merfolk`
범위: `TODO.md`의 High Priority 전체 (Architecture 3 + Security 4 + core 테스트) + `NamingConvert.toCamelCase` 수정(Medium에서 승격)

## 배경

`TODO.md`의 High Priority 항목을 조사하는 과정에서 두 건이 문서상 결함이 아니라 실제 런타임 결함으로 확인됐다.

**`saveAll`은 현재 `ClassCastException`이 난다.** `UserRepository extends IXRepository<Integer, User>`이므로 `saveAll`은 `Integer` 반환으로 선언되지만, `XRepositoryProxy.handleSaveAll()`은 `commonMapper.insertAll()`의 `Long`을 반환한다. JDK 동적 프록시가 선언 타입으로 캐스팅하면서 실패한다. 빈 리스트일 때만 `return 0`(Integer 리터럴)이라 우연히 통과한다. `K`가 `Long`인 리포지토리에서만 정상 동작해 왔다.

**`XSessionResolver`는 인증을 조용히 건너뛴다.** `XWebMvcConfiguration:23`이 `@Autowired(required = false)`이므로 사용자가 `XAccessTokenParseHandler` 빈을 등록하지 않으면 필드가 `null`이 되고, `XSessionResolver.resolveArgument()`가 `null`을 반환해 컨트롤러가 인증 없이 실행된다.

## 핵심 제약: SessionData는 주입 대상이 아니다

`XRestEnvironment` 정적 싱글톤이 존재하는 이유는 `SessionData` 때문이다. `SessionData`는 Jackson이 액세스 토큰에서 역직렬화하는 POJO라 Spring 빈이 될 수 없는데, `isExpire()`는 `axim.rest.session.token-expire-days` 설정값이 필요하다. 생성자 주입이 불가능한 객체가 설정에 접근하려니 정적 홀더가 도입됐다.

따라서 싱글톤 제거는 DI 배선 문제가 아니라 책임 배치 문제다. 나머지 3개 사용처는 모두 Spring이 관리하므로 주입이 자명하다.

## 1. XRestEnvironment DI 전환

`XRestEnvironment`에서 `private static volatile XRestEnvironment instance` 필드, 생성자의 `instance = this` 대입, `getInstance()`를 삭제한다. `@Component`와 생성자 주입은 유지한다.

사용처별 전환:

| 사용처 | 전환 방식 |
|---|---|
| `XRequestFilter` | 생성자 주입 (`@Component`이므로 자명) |
| `XRestClientProxy` | 생성자 주입 (이미 `ApplicationContext` 보유) |
| `XBaseAccessTokenHandler` | `@Value` 필드 주입 + `protected` 게터 |
| `SessionData` | 의존 제거 — 만료 판단을 핸들러로 이동 |

### XBaseAccessTokenHandler에 생성자 주입을 쓰지 않는 이유

사용자가 이 클래스를 상속해 빈으로 등록하는 것이 이 프레임워크의 확장 지점이다. 생성자에 파라미터를 추가하면 기존 서브클래스가 전부 컴파일 에러가 난다. `@Value` 필드 주입은 무인자 생성자를 유지하므로 서브클래스에 영향이 없고, 정적 전역 상태를 제거한다는 목표는 동일하게 달성한다.

```java
@Value("${axim.rest.session.secret-key:#{null}}")
private String secretKey;

@Value("${axim.rest.session.token-expire-days:90}")
private int tokenExpireDays;

protected String getSecretKey() { return secretKey; }

@Override
public int getTokenExpireDays() { return tokenExpireDays; }
```

게터를 `protected`/`public`으로 두면 서브클래스가 오버라이드할 수 있고, 테스트에서도 오버라이드로 값을 주입할 수 있다.

### SessionData.isExpire 시그니처 변경

```java
// SessionData
public boolean isExpire(int expireDays) { ... }   // XRestEnvironment 의존 제거

// XAccessTokenParseHandler
default int getTokenExpireDays() { return 90; }

default <R extends SessionData> R validateSession(...) {
    ...
    if (session.isExpire(getTokenExpireDays())) {
        throw new UnAuthorizedException(UnAuthorizedException.EXPIRE_ACCESS_TOKEN);
    }
}
```

`SessionData`는 순수 데이터 객체가 되어 설정 없이 단위 테스트가 가능해진다.

**Breaking change:** `isExpire()`를 직접 호출하는 외부 코드는 컴파일 에러가 난다. 프레임워크 내부 호출처는 `XAccessTokenParseHandler:38` 한 곳뿐이다. 무인자 오버로드를 남기지 않는 이유는, 남길 경우 설정을 무시하고 90일로 고정 동작해 조용히 잘못된 결과를 내기 때문이다. 컴파일 에러가 조용한 오동작보다 낫다.

## 2. Security

### 2.1 secret key 미설정 경고

`XBaseAccessTokenHandler`에 `@PostConstruct`를 추가해 `secretKey`가 null이면 기동 시 1회 `log.warn`한다. 서명 없는 plain Base64 토큰은 누구나 위조할 수 있으므로 경고 문구에 그 사실을 명시한다. 요청마다 찍으면 로그가 범람하므로 기동 시 1회로 제한한다.

### 2.2 makeServiceUrl 기본 스킴

`XRestClient.makeServiceUrl()`의 `"http://"` → `"https://"`. `serviceHost`가 이미 스킴을 포함한 경우의 분기는 그대로 둔다.

**Breaking change:** 스킴 없이 평문 HTTP 서비스를 호출하던 설정은 깨진다. 해결책은 설정에 `http://`를 명시하는 것이며, 이는 의도를 드러내므로 바람직하다.

### 2.3 XSessionResolver 인증 갭 — 기동 시 fail-fast

새 컴포넌트 `XSessionConfigurationValidator`(`rest-api/.../configuration/`)를 추가한다. `ApplicationListener<ContextRefreshedEvent>`로 동작하며:

1. `RequestMappingHandlerMapping.getHandlerMethods()`를 순회한다
2. `SessionData`가 할당 가능한 파라미터를 가진 핸들러 메서드를 찾는다
3. 그런 메서드가 있는데 `XAccessTokenParseHandler` 빈이 없으면 `IllegalStateException`을 던져 기동을 실패시킨다

예외 메시지에는 문제가 된 컨트롤러 메서드와 해결 방법(`XAccessTokenParseHandler` 구현 빈 등록)을 포함한다. 별도 컴포넌트로 분리하는 이유는 `XWebMvcConfiguration`에 검증 책임까지 얹지 않기 위해서다.

### 2.4 XRequestFilter 파라미터 마스킹

이미 존재하는 `SENSITIVE_HEADERS` 패턴을 그대로 따라 `SENSITIVE_PARAMS`를 추가한다.

```java
private static final Set<String> SENSITIVE_PARAMS = Set.of(
        "password", "passwd", "pwd", "secret", "token",
        "access-token", "accesstoken", "api-key", "apikey", "credential"
);
```

`getRequestParameterString()`에서 파라미터명을 소문자로 비교해 일치하면 값을 `***`로 대체한다.

**범위 밖(별도 이슈):** develop 프로파일에서 JSON 요청 본문을 통째로 MDC에 넣는 경로(`XRequestFilter:77-81`)도 동일한 유출 위험이 있다. 본문 마스킹은 JSON 파싱이 필요해 이번 범위에서 제외하되 TODO에 추가한다.

## 3. saveAll 반환 타입

```java
// IXRepository
int saveAll(List<T> entities);   // K → int
```

`XRepositoryProxy.handleSaveAll()`은 `Long`을 `intValue()`로 변환해 반환하고, null이면 0을 반환한다. `CommonMapper.insertAll()`의 `Long` 반환은 MyBatis 관례이므로 유지하고 프록시 경계에서 변환한다.

`INSERT IGNORE` 배치에서는 중복 키 행이 건너뛰어지므로 "마지막 삽입 PK"는 의미가 모호하다. 영향받은 행 수가 실제 동작이자 유일하게 의미 있는 반환값이다.

**Breaking change:** `K`가 `Long`이라 우연히 동작하던 코드는 컴파일 에러가 난다. 그 외 모든 `K`에서는 이미 `ClassCastException`이 나던 코드다.

## 4. XResultInterceptor.setParameters 제거

손으로 복제한 `setParameters()`(약 45줄)를 삭제하고 MyBatis에 위임한다.

```java
configuration.newParameterHandler(mappedStatement, parameterObject, boundSql)
             .setParameters(ps);
```

`XResultInterceptor`는 `Executor.query`/`Executor.update`만 인터셉트하므로(`@Intercepts`, 63-67행), `newParameterHandler()`가 적용하는 `ParameterHandler` 플러그인 체인과 겹치지 않는다. 재귀 위험이 없다. 위임 방식은 사용자가 등록한 다른 `ParameterHandler` 플러그인도 함께 존중한다는 부수 효과가 있다.

호출처는 `XResultInterceptor:234`(COUNT 쿼리 실행) 한 곳이다.

## 5. NamingConvert.toCamelCase 수정

현재 `toCamelCase("a__b")`는 `"a_b"`를 반환한다. `_`를 지운 뒤 다음 문자를 대문자화하는데, 다음 문자가 또 `_`라 그대로 남는다.

TDD로 진행한다. 실패하는 테스트를 먼저 쓰고 고친다. 기대 동작은 연속 언더스코어를 하나로 취급하는 것이다.

수정 대상은 연속 언더스코어 케이스뿐이며, 나머지 현재 동작은 그대로 보존해야 한다:

| 입력 | 현재 | 기대 | 비고 |
|---|---|---|---|
| `user_name` | `userName` | `userName` | 유지 |
| `a__b` | `a_b` | `aB` | **수정 대상** |
| `a___b` | `a__b` | `aB` | **수정 대상** |
| `_abc` | `Abc` | `Abc` | 유지 |
| `abc_` | `abc` | `abc` | 유지 |
| `null` / `""` | 그대로 | 그대로 | 유지 |

Medium 항목이지만 High 범위로 승격한다. 고치지 않고 테스트만 쓰면 `assertEquals("a_b", toCamelCase("a__b"))`처럼 버그를 정답으로 박제하게 되고, 나중에 수정할 때 그 테스트가 방해가 된다. 테스트는 명세를 기록하는 문서이지 현재 동작의 스냅샷이 아니다.

## 6. core 모듈 테스트

`core/build.gradle`에 `spring-boot-starter-test`가 이미 있으므로 JUnit 5 + AssertJ를 바로 쓸 수 있다. 신규 인프라는 불필요하다.

### NamingConvertTest
- `toCamelCase`: 기본 변환, **연속 언더스코어(`a__b` → `aB`)**, 선행/후행 언더스코어, null/빈 문자열
- `toUnderScoreName`: 기본 변환, 연속 대문자(`XMLHttpRequest`), null/빈 문자열
- `toCamelCaseByClassName`: 기본 변환, 빈 파트

### XPaginationTest
- `getOffset()`이 **`page`/`size` 기반 계산을 우선**하고 `setOffset()`은 사실상 무시된다는 현재 동작 고정
- `page=1` → offset 0, `page=3, size=20` → offset 40
- `hasLimit()` 경계

`getOffset()`은 `page` 기본값 1, `size` 기본값 20이므로 `page > 0 && size > 0`이 항상 참이고, 따라서 `setOffset()`으로 설정한 값은 `page` 또는 `size`를 명시적으로 0으로 만들지 않는 한 반환되지 않는다. **이 동작은 이번에 바꾸지 않는다** — 바꾸면 기존 사용자의 페이징 결과가 조용히 달라진다. 테스트로 고정하고 Javadoc에 우선순위 규칙을 명시한다.

### XPageTest
- `setPageRowsByObject`: `List` 정상 처리, null → null, 非List → `IllegalArgumentException`
- `getHasNext()`: 마지막 페이지 false, 중간 페이지 true, null 필드 방어, `size=0` 방어

### SessionDataTest (rest-api)
- `isExpire(int)`: 만료/미만료 경계, 잘못된 `createDate` 포맷 → true

`SessionData`가 순수 데이터 객체가 되면서 처음으로 단위 테스트가 가능해진다. 이것이 1번 리팩터링의 실질적 성과를 증명하는 테스트다.

## 검증

- `./gradlew build`가 통과해야 한다 (전체 모듈 컴파일 + 테스트)
- `grep -rn "XRestEnvironment.getInstance" core rest-api mybatis demo`가 **0건**이어야 한다
- 신규 테스트가 모두 통과해야 한다
- `NamingConvert.toCamelCase` 수정은 TDD 순서를 지켰음이 커밋에서 드러나야 한다

## 문서 갱신

- `TODO.md`: 처리한 High 항목 체크, `toCamelCase` Medium 항목 제거, XRequestFilter 본문 마스킹 항목 추가
- Breaking change 4건(`isExpire`, `saveAll`, `makeServiceUrl` 스킴, `getInstance` 제거)은 릴리스 노트에 명시 — 버전을 1.4.0으로 올린다 (patch가 아니라 minor)
