# ADR/RFC-0001 — HTTP 클라이언트 계층 재설계

| | |
|---|---|
| **상태** | Proposed (결정 대기) |
| **작성일** | 2026-07-17 |
| **대상 버전** | 1.4.x (비파괴 단계) → 2.0.0 (제거 단계) |
| **범위** | `rest-api` 모듈의 `XRestClient` / `XWebClient` / `XRestClientProxy` / `@XRestService` |
| **관련 태스크** | T6(REST 클라이언트 계층 수정), T9(mapper 팩토리·테스트), T11(가드레일) |
| **이 문서의 성격** | **결정 문서.** 코드 이관은 승인 후 후속 태스크(§10)로 분리한다. |

---

## 1. 요약 (TL;DR)

**권고: 옵션 A′ — RestClient 단일 transport + 내부 공용 코어 추출.**
공개 API는 선언적(`@XRestService`) / 프로그래밍(`XWebClient`) **2종을 유지**하되, 그 아래 transport는
RestClient **1종**으로 통일하고, RestTemplate 경로는 deprecate 후 2.0에서 제거한다.
옵션 B의 "공용 코어 추출"은 채택하되 **transport SPI를 공개하지 않는다**(§4.4).

조사 중 **설계 판단을 뒤집는 사실**을 발견했다(§3):

> `java.util.Date` 를 포함한 DTO는 **현재 Axim 서비스 간 통신에서 이미 깨져 있다.**
> `XRestClient` 의 하드코딩된 `setDateFormat("yyyy-MM-dd HH:mm:ss")` 가 Spring Boot 서버의
> 기본 Jackson 직렬화(ISO-8601)와 **양방향 모두 InvalidFormatException** 을 일으킨다.
>
> 근본 원인(§5.0): 문서가 "JSON date format is `yyyy-MM-dd HH:mm:ss`, not ISO 8601" 을 시스템 불변식으로
> 4곳에 선언했지만, 그 불변식은 **클라이언트에만 구현**되었고 **서버에는 구현된 적이 없다**
> (`spring.jackson.*` 설정이 레포·문서 어디에도 없고, 서버측 Jackson 커스터마이징 0건).

따라서 이 재설계는 "기술 부채 정리"가 아니라 **기존 결함 수정**이며, mapper 일원화(§5)는
선언적 클라이언트 이관(§4)의 **선행 조건**이지 독립적인 청소 작업이 아니다. 순서가 중요하다.
그리고 정합화 방향은 **클라이언트를 서버에 맞추는 것(Fix-A)** 이다 — 반대(Fix-B)는
모든 Axim 서비스의 공개 HTTP API를 파괴한다(§5.1).

---

## 2. 배경과 문제 정의

### 2.1 현재 구조

```
[선언적 API]  @XRestService 인터페이스
                  └─ XRestClientProxy (JDK Proxy, InvocationHandler)
                        └─ XRestClient ──────────► RestTemplate ──┐
                                                                   ├─► httpclient5 (PoolingHttpClientConnectionManager)
[프로그래밍 API]  XWebClientFactory.create(baseUrl)                │
                        └─ XWebClient ───────────► RestClient ────┘
```

두 경로는 **transport(RestTemplate vs RestClient)** 와 **API 스타일(선언적 vs 프로그래밍)** 이라는
서로 다른 두 축이 우연히 1:1로 묶여 있다. 이것이 문제의 핵심이다 — 선언적 API를 쓰려면
자동으로 레거시 transport를 쓰게 되고, 최신 transport를 쓰려면 선언적 API를 포기해야 한다.

### 2.2 중복의 실제 목록 (코드 확인)

| 중복 항목 | XRestClient (RestTemplate) | XWebClient (RestClient) |
|---|---|---|
| 에러 파싱 | `throwRestException:280-306` | `handleErrorResponse:194-226` |
| 연결 실패 처리 | `UnavailableServerException(UNAVAILABLE_SERVICE, serviceName)` | `UnavailableServerException(UNAVAILABLE_SERVICE)` — **serviceName 누락** |
| ObjectMapper | `createObjectMapper:258-264` | `createObjectMapper:228-234` (**에러 파싱 전용**) |
| 응답 역직렬화 | 수동 (`String.class` → Jackson) | HttpMessageConverter |
| debug 로깅 | `isDebug` 필드 | `isDebug` 필드 |

세 번째 ObjectMapper 사본은 `XBaseAccessTokenHandler:24-31` 에 있다.

### 2.3 이미 존재하지만 배선되지 않은 추상화

`handler/DefaultErrorResponseHandler.java` 는 **정확히 이 중복을 없애기 위한 클래스**이며
`ObjectMapper` 를 주입받아 `ApiError` 파싱을 수행한다. 그러나 **레포 전체에서 참조 0건 — 완전한 dead code**다.
두 클라이언트는 이 클래스를 쓰는 대신 각자 같은 로직을 인라인으로 복사해 두었다.
즉 **통합 지점은 이미 설계되어 있고, 배선만 안 되어 있다.**

### 2.4 비대칭 (중요)

`XWebClient` 의 `OBJECT_MAPPER` 는 **에러 바디 파싱에만** 쓰인다. 성공 응답은 `.body(responseType)` 을 통해
RestClient의 **기본 HttpMessageConverter** 가 처리한다. 반면 `XRestClient` 는 성공/에러 **양쪽 모두**
자기 `OBJECT_MAPPER` 로 처리한다(`request:226-233` 이 항상 `String.class` 로 받은 뒤 수동 매핑).

→ **두 클라이언트는 성공 응답의 직렬화 의미가 서로 다르다.** 이것이 §3의 발견으로 이어진다.

---

## 3. 실측 근거 (PoC)

주장을 검증하기 위해 실제 jar(`spring-web-6.1.14`, `jackson-2.17.2`)로 PoC를 실행했다.
스크립트: `scratchpad/poc/MapperDelta.java`, `scratchpad/poc/ServerFormatProbe.java` (레포 외부, 커밋 대상 아님).

### 3.1 세 개의 "복붙" mapper는 사실 서로 다르게 동작한다

```
framework  (XRestClient/XWebClient)     -> {"legacyDate":"2023-11-14 22:13:20", "modernDate":"2023-11-14T22:13:20"}
tokenHandler (XBaseAccessTokenHandler)  -> {"legacyDate":1700000000000,          "modernDate":[2023,11,14,22,13,20]}
restClient default converters           -> {"legacyDate":1700000000000,          "modernDate":[2023,11,14,22,13,20]}
```

원인: `setDateFormat(...)` 호출은 Jackson에서 `WRITE_DATES_AS_TIMESTAMPS` 를 **암묵적으로 비활성화**한다.
`XBaseAccessTokenHandler` 는 `setDateFormat` 을 부르지 않으므로 `LocalDateTime` 이 **숫자 배열**로 직렬화된다.

> **결론 1:** "3벌 복붙 제거"는 순수 리팩터링이 **아니다**. 어떤 설정을 정본으로 삼든
> 최소 한 컴포넌트의 wire format이 바뀐다. RFC가 정본을 명시적으로 결정해야 한다(§5).

### 3.2 v1.3.2(a32c783) 버그의 근본 원인 확인

```
bare new ObjectMapper()  -> THROWS InvalidDefinitionException:
                            Java 8 date/time type `java.time.LocalDateTime` not supported by default
restClient default       -> 자동 등록 모듈: [Jdk8Module, parameter-names, jackson-datatype-jsr310]
```

`new ObjectMapper()` 는 모듈을 자동 탐색하지 않는다. `Jackson2ObjectMapperBuilder`(Spring)는 classpath의
well-known 모듈을 **자동 등록**한다. a32c783이 3곳에 `registerModule(new JavaTimeModule())` 을 복붙해야 했던 이유가 이것이다.

> **결론 2:** `Jackson2ObjectMapperBuilder` 기반 팩토리로 전환하면 JavaTimeModule 불변식이
> **복붙 규약이 아니라 구조적으로 보장**된다. (T9-P1-6의 근본 해결)

### 3.3 ★ `java.util.Date` 는 이미 깨져 있다 (양방향)

> **검증 노트 (2026-11-10, v1.5.0 `WireContractTest`):** 실제 `XRestClientProxy` 경로로 확인한 결과 **깨지는 것은 응답 방향뿐이다.**
> `@XRestService` 요청 바디는 `XRestClient` 의 ObjectMapper 가 아니라 RestTemplate 기본 컨버터로 직렬화된다
> (`java.util.Date` → epoch millis, `LocalDateTime` → 배열). Boot 서버는 둘 다 읽는다. 아래 PoC 의
> "Boot 서버가 XRestClient 요청 바디를 읽을 수 있는가 → THROWS" 는 ObjectMapper 단독 PoC 결과이며 실제 요청 경로와 다르다.
> 응답 방향(`XRestClient` 가 서버의 ISO-8601 `java.util.Date` 를 읽지 못함)은 재현됐다.

```
Boot SERVER emits        : {"legacyDate":"2023-11-14T22:13:20.000+00:00", "modernDate":"2023-11-14T22:13:20"}
XRestClient mapper emits : {"legacyDate":"2023-11-14 22:13:20",           "modernDate":"2023-11-14T22:13:20"}

XRestClient가 Boot 서버 응답을 읽을 수 있는가?
  -> THROWS InvalidFormatException: Cannot deserialize `java.util.Date` from "2023-11-14T22:13:20.000+00:00"
Boot 서버가 XRestClient 요청 바디를 읽을 수 있는가?
  -> THROWS InvalidFormatException: Cannot deserialize `java.util.Date` from "2023-11-14 22:13:20"
```

Spring Boot의 `JacksonAutoConfiguration` 은 `WRITE_DATES_AS_TIMESTAMPS` 를 끄므로 서버는 ISO-8601을 낸다.
`XRestClient` 는 하드코딩된 `"yyyy-MM-dd HH:mm:ss"` 를 기대한다. **서로 읽지 못한다.**

**정직한 한정 조건:**
- `java.time.*` 은 양쪽 다 `"2023-11-14T22:13:20"` 로 일치 → **정상**. 깨지는 것은 **legacy `java.util.Date` 뿐**이다.
- 소비자가 서버에 `spring.jackson.date-format=yyyy-MM-dd HH:mm:ss` 를 설정하면 우연히 맞는다.
  즉 현재 프레임워크는 **모든 Axim 서버의 Jackson 설정을 클라이언트의 하드코딩 값에 암묵적으로 결합**시키고 있다 — 문서화된 바 없다.
- demo 엔티티(`User`)에 Date 필드가 없어서 아무도 못 봤다. 테스트도 없다(T9-P1-5).

> **결론 3:** 옵션 A는 "위험을 감수하는 이관"이 아니라 **기존 결함의 수정**이다.
> Boot 기본값(ISO-8601)으로 정렬하면 클라이언트와 서버가 처음으로 일치한다.

---

## 4. 목표 아키텍처 선택지 비교

### 4.1 비교표

| 기준 | (A) RestClient 단일화 | (B) 공용 코어 + transport 추상화 | (C) 현행 유지 + RestTemplate 경로만 정리 |
|---|---|---|---|
| 개념 중복 해소 | ✅ transport 1종 | ⚠️ transport SPI가 새 개념을 **추가** | ❌ 이원화 유지 |
| 에러처리 2벌 | ✅ 1벌 | ✅ 1벌 | ⚠️ 부분 (수동 동기화 지속) |
| mapper 3벌 | ✅ 1벌 | ✅ 1벌 | ⚠️ 별도 작업 필요 |
| §3.3 Date 결함 | ✅ 수정됨 | ✅ 수정됨 | ⚠️ 별도 수정 필요 |
| RestTemplate 탈출 | ✅ 완전 | ⚠️ "옵션화" 시 잔존 | ❌ 잔존 |
| 선언적 API 현대화 | ✅ | ✅ | ❌ (`@XRestService` 가 레거시에 고착) |
| 구현 비용 | **중** | **높음** | 낮음 |
| 파괴 범위 | 중 (§7) | 중~높음 (SPI 공개 시 영구 부담) | 없음 |
| 과잉설계 위험 | 낮음 | **높음** — 구현체 1개짜리 SPI | 없음 |
| 미래 비용 | 낮음 | SPI 하위호환 영구 유지 | **높음** (maintenance-mode 의존 누적) |

### 4.2 옵션 C를 기각하는 이유

RestTemplate은 maintenance mode(신규 기능 없음)다. C는 §3.3의 Date 결함, 에러처리 2벌, mapper 3벌을
**전부 별도 작업으로 남긴다.** 그 별도 작업들의 합은 이미 A의 비용에 근접하는데,
결과물은 여전히 이원화 구조다. **가장 싸 보이지만 가장 비싼 선택**이다.

단 하나의 장점(파괴 없음)은 §7의 단계적 계획으로 A에서도 1.4.x 동안 확보된다.

### 4.3 옵션 B를 부분 채택하는 이유

B의 **"공용 코어 추출"** 은 옳다 — §2.2의 중복은 실재하고, §2.3의 `DefaultErrorResponseHandler` 가
이미 그 방향을 가리킨다. 채택한다.

그러나 B의 **"transport 추상화 SPI"** 는 기각한다. 우리가 원하는 transport는 RestClient **하나**이고,
RestTemplate은 **제거 대상**이다. 구현체가 하나뿐인 인터페이스를 공개 SPI로 만들면
speculative generality이며, 한 번 공개하면 하위호환을 영구히 져야 한다.
**"둘을 지원하기 위한 추상화"가 아니라 "하나로 줄이기 위한 삭제"가 필요하다.**

### 4.4 권고: A′ = A(전략) + B의 공용 코어(내부 구현 한정)

```
[선언적 API]  @XRestService                     ← 공개, 유지
                  └─ XRestClientProxy
                        └─┐
[프로그래밍 API]  XWebClient                     ← 공개, 유지
                        └─┤
                          ├─► (internal) XHttpExchange       ─ 요청 실행 1곳
                          ├─► (internal) XErrorTranslator    ─ 에러 파싱 1곳 (DefaultErrorResponseHandler 배선)
                          ├─► (internal) XJsonMapperFactory  ─ ObjectMapper 1곳 (T9)
                          └─► RestClient ──► httpclient5
```

- 공개 API 2종 유지: 선언적/프로그래밍은 **정당한 두 사용 방식**이다(Feign vs RestTemplate과 같은 구도).
  이것은 제거해야 할 중복이 아니다. 제거 대상은 **transport 중복**이다.
- 공용 코어 3종은 **package-private / `internal` 패키지**로 두고 공개 API로 승격하지 않는다.

---

## 5. ObjectMapper 일원화 (검토항목 3, T9 연동)

### 5.0 근본 원인: 문서화된 불변식이 한쪽에만 구현되어 있다

§3.3의 결함이 왜 생겼는지 확인했다. 문서는 다음을 **시스템 전체의 불변식**으로 4곳에 선언한다:

> "JSON date format is `yyyy-MM-dd HH:mm:ss`, not ISO 8601."
> — `SKILL.md:21`, `docs/guide.md:16`, `docs/guide.md:984`, `references/rest-client-and-auth.md:532`

그러나 코드 확인 결과:

| | 실제 동작 | 근거 |
|---|---|---|
| **클라이언트** (XRestClient/XWebClient) | `yyyy-MM-dd HH:mm:ss` | `setDateFormat(...)` 하드코딩 |
| **서버** (Axim 서비스의 REST 응답) | **ISO-8601** | Boot `JacksonAutoConfiguration` 기본값 |
| 서버를 문서대로 맞추는 설정 | **존재하지 않음** | `spring.jackson.*` 이 레포·문서 어디에도 없음 |
| 프레임워크의 서버측 Jackson 커스터마이징 | **0건** | `rest/configuration/` 내 `ObjectMapper`/`Jackson2ObjectMapperBuilder` 참조 없음. `XWebMvcConfiguration.extendMessageConverters` 는 charset/media type만 건드림 |

> **즉, 불변식은 클라이언트에만 구현되었고 서버에는 구현된 적이 없다.** 문서는 구현되지 않은 계약을 선언하고 있으며,
> 이를 검증하는 테스트도 없다(T9-P1-5). §3.3의 양방향 실패는 이 비대칭의 직접적 귀결이다.
> (T9의 문서 검토에서 나온 D-1(`@XDefaultValue`)과 **동일한 패턴** — 문서가 코드보다 앞서 나간 사례.)

### 5.1 정본 결정 (RFC가 결정해야 할 핵심 항목)

§5.0에 따라 정합화 방향은 두 가지뿐이다. **둘 중 하나는 반드시 골라야 한다.**

| | **Fix-A: 클라이언트를 서버에 맞춘다** (권고) | **Fix-B: 서버를 문서에 맞춘다** (기각) |
|---|---|---|
| 방법 | `setDateFormat` 제거 → ISO-8601 | 프레임워크가 서버측에 `spring.jackson.date-format=yyyy-MM-dd HH:mm:ss` 강제 |
| 서비스의 **공개 HTTP API** 변화 | **없음** — 서버는 이미 ISO-8601을 내보내는 중 | **모든 Axim 서비스의 응답 포맷이 바뀜** |
| 영향 받는 대상 | 프레임워크 내부 클라이언트 경로뿐 | **모든 외부 소비자** (브라우저·모바일·타 팀 서비스) |
| 문서 | 4곳 수정 필요 (불변식 삭제) | 문서 유지 |
| 성격 | 깨진 쪽(클라이언트)을 고침 | 멀쩡히 동작 중인 쪽(서버)을 깨뜨림 |
| 판정 | ✅ **채택** | ❌ **기각** — blast radius가 비교 불가하게 큼 |

**Fix-B 기각 사유:** 서버는 현재 ISO-8601을 내보내고 있고, Axim 서비스의 **기존 외부 소비자들은 이미 그 포맷에 의존**하고 있다.
Fix-B는 "프레임워크 내부 클라이언트의 버그"를 고치려고 **모든 서비스의 공개 API 계약을 파괴**한다.
문서화된 불변식이 그럴싸해 보인다는 이유만으로 실제로 동작 중인 계약을 바꿀 수는 없다.
→ **문서가 틀렸다. 문서를 고친다.**

**권고: Fix-A — `Jackson2ObjectMapperBuilder.json().build()` 기반, `setDateFormat` 제거.**

```java
// (internal) XJsonMapperFactory — 단일 정의
static ObjectMapper create() {
    return Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // Boot 서버와 동일
            .build();  // JSR310/Jdk8/parameter-names 자동 등록 (§3.2)
}
```

근거:
1. §3.3의 Date 결함이 **수정된다** — 클라이언트가 Boot 서버와 같은 포맷을 말하게 된다.
2. §3.2에 의해 JavaTimeModule 불변식이 구조적으로 보장된다 → a32c783 류 재발 불가.
3. RestClient의 기본 converter 동작과 일치 → §6의 이관에서 **추가 변환 계층이 불필요**해진다.
4. `FAIL_ON_UNKNOWN_PROPERTIES` 는 양쪽 다 이미 `false` → **델타 없음**(§3 PoC 3번).

### 5.2 파괴적 영향과 완화

영향 받는 유일한 집단: 서버에 `spring.jackson.date-format=yyyy-MM-dd HH:mm:ss` 를 **직접 설정해서**
현재의 클라이언트 하드코딩과 우연히 맞춰 둔 소비자. (프레임워크가 시킨 적은 없으므로 §5.0대로 문서에도 없다.)
이들은 Fix-A 이후 클라이언트가 ISO를 말하게 되어 어긋난다.

- 1.4.x: `axim.rest.json.legacy-date-format=true` 탈출구 제공(기본 `false`). 사용 시 WARN 로그.
- 2.0.0: 탈출구 제거.
- **문서 정정 (필수, T12.1 범위)**: §5.0의 4곳에서 "JSON date format is `yyyy-MM-dd HH:mm:ss`, not ISO 8601"
  불변식을 **삭제**하고 "ISO-8601(Boot 기본값), `java.util.Date` 대신 `java.time.*` 사용 권장"으로 대체.
  §3.3에서 `java.time.*` 은 이미 양쪽 정상이므로 이 권고는 즉시 안전하다.

> ⚠️ **T11과의 충돌 주의:** `SKILL.md` 는 현재 T11(가드레일)이 수정 중이다(워킹트리에서 확인).
> `SKILL.md:21` 이 §5.0의 잘못된 불변식을 담고 있는 바로 그 줄이므로, T12.1의 문서 정정은
> **T11 머지 이후**에 수행하거나 T11 담당자와 조율해야 한다. 본 태스크에서는 `SKILL.md` 를 건드리지 않았다.

### 5.3 T9와의 경계

- **T9**: mapper 팩토리 생성 + `LocalDateTime` 왕복 테스트 (T9-P1-6). **이 RFC의 선행 작업.**
- **T12(본 문서)**: 그 팩토리의 **정본 설정을 무엇으로 할지 결정**하고, §3.3 결함을 드러냄.
- 충돌 주의: T9가 "현행 동작 보존"을 목표로 3벌을 그대로 합치면 §3.1에 의해 **불가능**하다
  (셋이 서로 다르므로). T9는 본 RFC의 §5.1 결정을 입력으로 받아야 한다. → **T9 착수 전 본 RFC 승인 필요.**

---

## 6. `@XRestService` 백엔드를 RestClient로 이관 시 영향 (검토항목 2)

`XRestClientProxy` 는 `XRestClient` 를 통해서만 동작하고, **`XRestClient` 의 직접 소비자는 레포 내 0건**이다
(demo의 언급은 주석뿐). 즉 `XRestClient` 내부를 갈아끼워도 **호출측 영향이 없다.**

| 기능 | 현재 (RestTemplate) | RestClient 이관 시 | 위험 |
|---|---|---|---|
| **응답 역직렬화** | `String.class` 수신 후 수동 Jackson (`request:226-233`) | converter 직접 사용 | **높음** — §5.1 mapper를 converter에 **주입 필수**. 안 하면 §3.1대로 포맷 변경 |
| **페이지네이션 자동변환** | `XPagination`→`size/page/offset` + `sort=col,DIR` 를 path에 **raw 문자열 append** (`XRestClientProxy:100-140`) | `.uri(uriBuilder -> …)` 로 queryParam 구성 권장 | 중 — 현재 raw append는 인코딩 미적용. 이관 시 **인코딩이 생겨** 값에 `,`/공백이 있으면 동작이 달라질 수 있음 |
| **path 변수** | `exchange(url, method, entity, String.class, Map<String,String> vars)` | `.uri(String, Map<String,?>)` **overload 사용 필수** | 중 — varargs overload를 쓰면 named `{id}` 확장이 깨짐 |
| **헤더 포워딩** | `@RequestHeader` → `HttpHeaders` | `.headers(h -> h.addAll(...))` | 낮음 |
| **form-urlencoded POST** | `@RequestBody` 없는 POST/PUT/PATCH는 `parameterMap`→`MultiValueMap`→`application/x-www-form-urlencoded` (`XRestClient:270-274`) | RestClient는 `MultiValueMap` body를 form으로 인코딩 | **중~높음** — XWebClient는 항상 JSON. **이 분기를 반드시 보존**해야 함 |
| **GET with body** | 지원 (`get(url, typeRef, requestBody, …)`) | RestClient도 지원 | 낮음 — 단 현재 이 분기는 `parameterMap` 을 **버린다**(기존 quirk, 보존 여부 결정 필요) |
| **에러 전파** | `throwRestException` → `XRestException` → proxy가 `setRemoteServiceName` | `.retrieve().onStatus(...)` 에서 throw | 낮음 — XWebClient가 이미 동일 패턴으로 동작 중 |
| **연결 실패** | `ResourceAccessException` → `UnavailableServerException(…, serviceName)` | 동일 예외 발생 | 낮음 — 단 XWebClient는 serviceName 누락(§2.2). **통합 시 serviceName 보존 쪽으로 정렬** |
| **`throws IOException`** | 18개 public 메서드가 `throws XRestException, IOException` 선언 | RestClient 경로는 IOException 미발생 | §7 참조 — **시그니처에서 제거하면 소스 호환 파괴** |

### 6.1 부수적으로 함께 고쳐지는 것들
- `XRestClient.makeServiceUrl:308-313` 의 기본 스킴 `http://` → `https://` (TODO Security 항목).
- `XRestClientProxy:39` 의 `@Value("${axim.rest.gateway.host:@null}")` — 리터럴 문자열 `"@null"` 버그(T9-P2-3).
- `XRestClientProxy:64` — `@XRestAPI` 없는 메서드가 조용히 `null` 반환 → `UnsupportedOperationException` 로 변경.

### 6.2 ★ 문서/코드 wire-contract 불일치 — `@RequestParam` 전송 방식 (독립 검증에서 추가 발견)
`references/rest-client-and-auth.md` pitfall #3 은 "@RequestParam creates query string, NOT form data" 라고 **단언**하지만, 실제 `XRestClientProxy` 는 POST/PUT/PATCH 에서 `@RequestBody` 가 없으면 `parameterMap` 을 **`MultiValueMap` 으로 `application/x-www-form-urlencoded` 전송**한다(§6 표 form-urlencoded 행과 동일 분기). 문서와 정반대다.
게다가 문서가 권하는 대안 `@RequestBody Map<String,String>` 은 `HashMap`(=`MultiValueMap` 아님)이라 `prepareHeaders` 가 `application/json` 을 붙여 **form 이 아니라 JSON 으로 나간다**. → **문서가 양쪽 다 틀렸다.**

이것은 단순 문서 오류가 아니라 **백엔드 교체(T12.4) 시 조용히 깨질 wire 계약**이다: RestClient 로 순진하게 이관하면 이 form-urlencoded 분기의 인코딩/Content-Type 이 달라져 상대 서버가 바디를 못 읽을 수 있다. §10.1 의 특성화 테스트(T12.0)가 **반드시 이 분기를 고정**해야 하며, 문서 정정은 T12.1(문서 정정 4곳)에 함께 포함한다. (§11-7 참조)

---

## 7. 호환성 / 버전 전략 (검토항목 5)

### 7.1 공개 API 표면
`IXRepository` 계열은 **무관**하다. 영향 범위는 `rest-api` 모듈에 한정된다.

| 심볼 | 성격 | 계획 |
|---|---|---|
| `@XRestService`, `@XRestAPI`, `@XHttpMethod` | 공개 | **변경 없음** (백엔드만 교체) |
| `XWebClient`, `XWebClientFactory` | 공개 | 유지 + 정리(§8) |
| `XErrorResponseHandler` | 공개 SPI | **변경 없음** |
| `XRestException`, `ApiError` | 공개 | **변경 없음** |
| `XRestClient` (public class, 18 overload) | 공개, 실사용 0건 | 1.4 deprecate → **2.0 제거** |

### 7.2 ★ `throws IOException` 함정
`XRestClient` 의 메서드에서 `throws IOException` 을 **제거하면** 이를 `catch (IOException e)` 하던 호출측이
**컴파일 에러**(unreachable catch)가 난다. 체크 예외 제거는 바이너리 호환이지만 **소스 비호환**이다.

→ 1.4.x 동안 `throws IOException` 을 **유지**한다(내부적으로 발생하지 않더라도).
제거는 2.0.0에서만 수행한다.

### 7.3 semver 판정
- **1.4.0 (minor, 비파괴)**: 내부 transport 교체, 공용 코어 추출, mapper 정본화(+legacy 탈출구), `XRestClient` deprecate.
  - 단 §5.2의 Date wire format 변경은 **동작 변경**이다. 엄밀히는 minor에 담기 부담스러우나,
    (a) 현재 동작이 §3.3대로 **이미 결함**이고, (b) 탈출구 property를 제공하므로 minor + 릴리스 노트 강조로 처리한다.
    **이 판단은 승인 대상이다** — 보수적으로 가려면 1.4를 건너뛰고 2.0에 몰 수 있다.
- **2.0.0 (major, 파괴)**: `XRestClient` 제거, `throws IOException` 제거, legacy date 탈출구 제거, RestTemplate/`makeRestTemplate` 빈 제거.

### 7.4 파괴적 변경 목록 (승인 필요)

| # | 변경 | 시점 | 완화 |
|---|---|---|---|
| B1 | `java.util.Date` wire format: `"yyyy-MM-dd HH:mm:ss"` → ISO-8601 | 1.4.0 | `axim.rest.json.legacy-date-format=true` (2.0에서 제거) |
| B2 | `XBaseAccessTokenHandler` 토큰 payload의 `LocalDateTime` 이 배열 → ISO 문자열 | 1.4.0 | 조건부. §7.5 참조 |
| B3 | `XRestClient` deprecated | 1.4.0 | 컴파일 경고만 |
| B4 | `XRestClient` 제거 | 2.0.0 | 1.4에서 1개 마이너 이상 유예 |
| B5 | `throws IOException` 제거 | 2.0.0 | §7.2 |
| B6 | `RestTemplate` / `makeRestTemplate` 빈 제거 | 2.0.0 | 소비자가 직접 정의하면 계속 사용 가능 |
| B7 | 기본 스킴 `http://` → `https://` | 1.4.0 | `axim.rest.client.default-scheme=http` 탈출구 |
| B8 | `@XRestAPI` 누락 메서드: `null` → 예외 | 1.4.0 | 사실상 버그 수정 |

### 7.5 B2의 실제 영향 범위 (코드 확인 결과 — 대부분 무해)

원 태스크 범위 밖에서 발견된 항목이라 정확히 한정해 둔다.

- 프레임워크 내장 `SessionData` 는 필드가 `String sessionId` / `String createDate` **둘 다 String** 이다
  (`createDate` 는 `DateTimeFormatter.ofPattern("yyyyMMddHHmmss")` 로 **직접 포맷한 문자열**).
  `java.time.*` / `java.util.Date` 필드가 **하나도 없다.**
  → **내장 `SessionData` 만 쓰는 소비자는 B2의 영향이 0이다.** 기존 토큰도 계속 유효하다.
- 단, `XBaseAccessTokenHandler.parse(request, cls)` 는 `readValue(decoded, cls)` 로
  **호출자가 지정한 클래스**에 역직렬화한다(`:82,:87`). 커스텀 세션 DTO에 `java.time.*` 필드를 넣은
  소비자가 있다면 그 소비자에 한해 **기존 발급 토큰이 파싱 불가**해진다.

> **판단:** B2는 "이론상 파괴적, 실제로는 커스텀 세션 DTO에 `java.time` 을 쓴 소비자에 한정".
> §11-6에서 해당 소비자 존재 여부만 확인하면 되고, 없으면 **T12.3은 불필요**하다.
> (참고: 내장 `SessionData` 에 시간 타입이 없다는 것은, a32c783이 `XBaseAccessTokenHandler` 에
> `JavaTimeModule` 을 등록한 것이 **커스텀 DTO만을 위한 것**이었음을 뜻한다.)

---

## 8. 에러 처리 통합과 T6와의 관계 (검토항목 4)

### 8.1 통합안
`DefaultErrorResponseHandler`(§2.3, 현재 dead code)를 **실제로 배선**하여 유일한 기본 파서로 만든다.

```java
// (internal) XErrorTranslator — 두 클라이언트가 공유
XRestException translate(HttpStatus status, String rawBody, @Nullable XErrorResponseHandler custom) {
    if (custom != null) {
        XRestException ex = custom.handle(status, rawBody);
        if (ex != null) { ex.setRawResponseBody(rawBody); return ex; }
    }
    return defaultHandler.handle(status, rawBody);  // ← DefaultErrorResponseHandler
}
```
`XRestClient.throwRestException` / `XWebClient.handleErrorResponse` 를 이 한 곳으로 대체한다.
동시에 §2.2의 `UnavailableServerException` serviceName 누락 불일치도 해소한다.

### 8.2 T6와의 경계 — **흡수 관계**

T6(H3/R10/R11: 두 클라이언트의 중복 에러 파싱 수정)는 **본 RFC의 §8.1이 완전히 흡수한다.**
같은 코드를 두 번 만지는 것은 낭비이며 충돌 위험이 있다.

**권고 — 다음 둘 중 하나를 선택 (승인 필요):**

- **(권장) T6를 본 RFC에 병합**: T6를 §10-T12.2로 흡수하고 T6는 닫는다.
  이유: T6가 "두 벌을 각각 고치면" 곧이어 §8.1이 그 두 벌을 **삭제**한다 → 작업이 버려진다.
- **(대안) T6를 최소 핫픽스로 한정**: 지금 당장 프로덕션 이슈가 있다면 T6는 **동작 수정만** 하고
  구조 변경(파일 이동/추상화 도입)은 하지 않는다. 이후 §10-T12.2가 구조를 정리한다.

> **T6 담당자와의 조율이 이 RFC 승인의 선결 조건이다.** 병렬 진행 시 확실히 충돌한다.

### 8.3 `XWebClientFactory` 공유 인스턴스 변이 (함께 수정)
`XWebClientFactory.create():18-23` 은 baseUrl별로 **동일 인스턴스를 캐시**하고,
`XWebClient.errorHandler():241` 은 `this` 를 **변이**시키고 `this` 를 반환한다.
→ 문서화된 `factory.create(url).errorHandler(...)` 패턴이 **같은 baseUrl의 다른 모든 소비자**
(프로퍼티로 등록된 `axim.web-client.services.*` 빈 포함)의 에러 핸들러를 덮어쓴다.
§8.1 작업 시 **핸들러별 사본 반환**(`withErrorHandler()` → 새 인스턴스)으로 함께 고친다.

---

## 9. 리액티브 필요성 판단 (검토항목 6)

**결론: 리액티브 WebClient는 명시적 비목표(non-goal).**

레포 전수 조사 결과:
- `reactor.*`, `Mono<`, `Flux<`, `.block()`, `webflux` 사용처 **0건**.
- `rest-api/build.gradle` 에 `spring-boot-starter-webflux` **의존 없음** (`starter-web` + `httpclient5`).
- 모든 소비 경로(`@XRestService`, `XWebClient`)가 **동기 시그니처**이며, 서버 측은 Servlet 스택(`XRequestFilter` 가 `jakarta.servlet.Filter`).

RestClient는 정확히 이 용도(동기 + fluent + webflux 불필요)로 만들어졌으므로 요구사항에 정합한다.
진짜 논블로킹이 필요해지면 그때 별도 ADR로 다룬다. **지금 리액티브를 위한 추상화를 넣지 않는다** — §4.3과 같은 논리다.

---

## 10. 후속 실행 태스크 분해안

각 태스크는 **독립 배포 가능**하도록 쪼갰다. 순서가 의미를 가진다(§1).

| ID | 작업 | 선행 | 파괴 | 규모 |
|---|---|---|---|---|
| **T12.1** | `XJsonMapperFactory` 도입 + 3벌 제거 + §5.1 Fix-A 적용 + `legacy-date-format` 탈출구 + **§5.2 문서 정정 4곳**(T11 머지 후). **T9와 통합 수행.** | 본 RFC 승인 | B1, B2 | S |
| **T12.2** | `XErrorTranslator` 추출 + `DefaultErrorResponseHandler` 배선 + 두 클라이언트 인라인 제거 + `XWebClientFactory` 변이 수정(§8.3). **T6 흡수.** | T12.1 | — | M |
| **T12.3** | (조건부) B2 무중단 필요 시 토큰 payload 이중 파싱(배열/ISO 모두 수용) 한시 지원 | T12.1 | — | S |
| **T12.4** | `XRestClient` 내부를 RestClient로 재구현. **public 시그니처·`throws IOException` 유지.** §6 표의 form-urlencoded/path변수/pagination 분기 **동작 보존 검증 필수** | T12.1, T12.2 | B7, B8 | **L** |
| **T12.5** | `XRestClient` `@Deprecated` + 마이그레이션 가이드 문서화 | T12.4 | B3 | S |
| **T12.6** | (2.0) `XRestClient` 제거, `throws IOException` 제거, RestTemplate 빈·의존 제거, legacy 탈출구 제거 | T12.5 | B4~B6 | M |

### 10.1 착수 전 반드시 필요한 것 — **T12.4의 전제**

T12.4는 §6 표의 8개 동작을 보존해야 하는데, **현재 `rest-api` 모듈의 테스트는 0개다**(T9-P1-5).
회귀를 탐지할 수단 없이 transport를 교체하는 것은 무모하다.

> **T12.0 (신규 제안, 최우선): `MockRestServiceServer` 기반 특성화 테스트(characterization test) 작성.**
> 교체 **전에** 현재 `XRestClient` 의 실제 동작(§6 표의 8행 + form-urlencoded + GET-with-body quirk)을
> 테스트로 고정한다. 네트워크 불필요. 이것이 T12.4의 안전망이며, T11(가드레일)과 직접 연결된다.

### 10.2 T9 / T11 과의 경계

- **T9** (mapper 팩토리·테스트): T12.1과 **동일 코드 영역**. → **T12.1로 통합 수행 권고.**
  단, T9는 본 RFC §5.1의 정본 결정을 입력으로 받아야 한다(§5.3).
- **T11** (가드레일): T12.0의 특성화 테스트가 T11의 커버리지 목표에 직접 기여. 충돌 없음. **상호 보완.**
- **T6** (REST 클라이언트 계층 수정): §8.2 참조. **충돌** — 병합 또는 핫픽스 한정 중 택일 필요.

---

## 11. 승인이 필요한 결정 사항

1. **옵션 A′ 채택 여부** (§4.4) — 공개 API 2종 유지 + transport 1종 + 내부 코어. transport SPI 미공개.
2. **§5.1 mapper 정본 — Fix-A vs Fix-B.** 클라이언트를 서버(ISO-8601)에 맞출 것인가(권고),
   서버를 문서(`yyyy-MM-dd HH:mm:ss`)에 맞출 것인가. **Fix-B는 모든 Axim 서비스의 공개 API를 바꾼다.**
   Fix-A 채택 시 §5.0의 문서 불변식 4곳을 삭제해야 한다.
3. **§7.3 버전 전략** — B1을 1.4.0(탈출구 제공)에 담을지, 2.0.0까지 미룰지.
4. **§8.2 T6 처리** — 본 RFC로 병합 vs 최소 핫픽스 한정.
5. **§10.1 T12.0 선행** — 특성화 테스트를 T12.4의 필수 선행으로 승인할지.
6. **B2 영향 확인** — `XBaseAccessTokenHandler.parse(request, cls)` 에 `java.time.*` 필드를 가진
   **커스텀 세션 DTO** 를 넘기는 소비자가 있는가? (내장 `SessionData` 는 §7.5에 따라 영향 없음.)
   없다면 T12.3을 드롭한다.
7. **§6.2 `@RequestParam` wire-contract** — POST/PUT/PATCH 의 form-urlencoded 분기(문서와 정반대)를
   T12.4 이관에서 **보존할지, 이 참에 정리할지** 결정. 어느 쪽이든 문서 정정(T12.1)과 특성화 테스트(T12.0)로 고정 필요.
   (독립 검증 워커가 추가 발견 — 코디네이터가 ADR에 병합함.)

---

## 12. 부록 — 조사에서 확인된 사실 (근거)

| 사실 | 확인 방법 |
|---|---|
| `DefaultErrorResponseHandler` 참조 0건 (dead code) | `grep -rn "DefaultErrorResponseHandler" --include=*.java .` |
| `XRestClient` 직접 소비자 0건 (프록시 경유만) | `grep -rn "XRestClient\b"` — demo 히트는 주석뿐 |
| 리액티브 사용 0건 | `grep -rn "reactor\.\|Mono<\|Flux<\|webflux\|\.block()"` |
| 3개 mapper의 wire format 상이 | `scratchpad/poc/MapperDelta.java` 실행 (§3.1) |
| bare `new ObjectMapper()` 가 `java.time` 미지원 | 동 PoC (§3.2) |
| `java.util.Date` 양방향 파싱 실패 | `scratchpad/poc/ServerFormatProbe.java` 실행 (§3.3) |
| `FAIL_ON_UNKNOWN_PROPERTIES` 델타 없음 | 동 PoC — 양쪽 다 `false` |
| 문서가 date 불변식을 4곳에 선언 | `grep -rn "yyyy-MM-dd HH:mm:ss" SKILL.md docs/guide.md references/*.md` |
| 서버를 그 불변식에 맞추는 설정 부재 | `grep -rn "spring.jackson"` — 레포·문서·demo 전부 0건 |
| 프레임워크의 서버측 Jackson 커스터마이징 0건 | `grep -rn "ObjectMapper\|Jackson2ObjectMapperBuilder" rest-api/.../configuration/` |
| 내장 `SessionData` 에 시간 타입 없음 (B2 한정) | `SessionData.java` — `sessionId`/`createDate` 둘 다 `String` |

PoC는 실제 의존 jar(`spring-web-6.1.14`, `jackson-2.17.2`, `spring-jcl-6.1.14`)로 실행했으며,
레포 외부(scratchpad)에 있고 커밋 대상이 아니다.
