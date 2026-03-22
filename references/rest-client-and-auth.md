# REST Client & Authentication Reference

## Declarative REST Client (@XRestService)

Requires `@XRestServiceScan` on the application class.

### Direct Mode vs Gateway Mode

```java
// Direct Mode — host specified -> URL: {host}{path}
@XRestService(value = "user-service", host = "${USER_SERVICE_HOST:http://localhost:8081}")
public interface UserServiceClient { ... }

// Gateway Mode — host omitted -> URL: {gatewayHost}/{serviceName}/{version}{path}
@XRestService(value = "user-service", version = "v1")
public interface UserServiceClient { ... }
```

### Parameter Annotations

```java
@XRestService(value = "order-service", host = "${ORDER_SERVICE_HOST}")
public interface OrderServiceClient {

    @XRestAPI(value = "/orders/{id}", method = XHttpMethod.GET)
    Order getOrder(@PathVariable("id") Long id);

    @XRestAPI(value = "/orders", method = XHttpMethod.POST)
    Order createOrder(@RequestBody OrderCreateRequest request);

    @XRestAPI(value = "/orders", method = XHttpMethod.GET)
    List<Order> search(@RequestParam("status") String status,
                       @RequestParam("keyword") String keyword);

    @XRestAPI(value = "/orders", method = XHttpMethod.GET)
    List<Order> getOrders(@RequestHeader("X-Tenant-Id") String tenantId);

    // XPagination auto-converted -> ?page=1&size=20&sort=createdAt,DESC
    @XRestAPI(value = "/orders", method = XHttpMethod.GET)
    XPage<Order> listOrders(XPagination pagination);

    @XRestAPI(value = "/orders/{id}", method = XHttpMethod.PUT)
    Order updateOrder(@PathVariable("id") Long id,
                      @RequestBody OrderUpdateRequest request,
                      @RequestHeader("Access-Token") String token);
}
```

### Error Handling

```java
try {
    Order order = orderClient.getOrder(id);
} catch (XRestException e) {
    e.getStatus();       // Original HTTP status (400, 404, 500, etc.)
    e.getCode();         // Error code from ApiError
    e.getMessage();      // Error message
    e.getDescription();  // Additional description
}
```

Error propagation preserves the original server's HTTP status code and ApiError (code, message, description) through the proxy chain.

## XWebClient (RestClient-based Alternative)

For programmatic HTTP calls (not declarative proxy).

### Option 1: Declarative Bean Registration via Properties

```properties
axim.web-client.services.userClient=http://user-service:8080
axim.web-client.services.orderClient=http://order-service:8080
```

```java
@Service
@RequiredArgsConstructor
public class ExternalApiService {

    @Qualifier("userClient")
    private final XWebClient userClient;

    public User getUser(Long id) {
        return userClient.get("/users/{id}", User.class, id);
    }
}
```

### Option 2: Programmatic via XWebClientFactory

```java
@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final XWebClientFactory webClientFactory;

    public User getUser(Long id) {
        XWebClient client = webClientFactory.create("http://external-api.com");
        return client.get("/users/{id}", User.class, id);
    }
}
```

### API Reference

```java
// Simple API
client.get("/users/{id}", User.class, id);
client.post("/users", body, User.class);
client.put("/users/{id}", body, User.class, id);
client.delete("/users/{id}", Void.class, id);

// Generic types
client.get("/users", new ParameterizedTypeReference<List<User>>() {});

// Builder API
client.spec()
        .get("/users?keyword=" + keyword)
        .header("X-API-Key", "my-key")
        .body(requestBody)
        .retrieve(new ParameterizedTypeReference<List<User>>() {});
```

| Feature | `@XRestService` | `XWebClient` |
|---|---|---|
| Style | Interface + annotations | Direct method calls |
| Bean creation | `@XRestServiceScan` | Properties or `XWebClientFactory` |
| Best for | Internal microservice calls | External API, dynamic URLs |
| Pagination | Auto XPagination -> query params | Manual query string |

## Error Propagation

`@XRestService` / `XWebClient` 호출 시 원격 서비스가 에러를 반환하면, 프레임워크가 자동으로 `XRestException`에 원본 정보를 보존하여 전파.

### 전파 흐름

```
[원격 서비스]                              [호출측]

에러 발생 -> XExceptionHandler             XRestClient/XWebClient 수신
  -> ApiError JSON 응답                      -> ApiError 파싱
  { code, message, description, data }       -> XRestException 생성
                                               (code, message, description, data 보존)
                                               (rawResponseBody = 원본 JSON 전체)
                                               (remoteServiceName = @XRestService value)
                                             -> 호출측에서 catch 가능
```

### XRestException 에러 정보 필드

| 필드 | 설명 |
|---|---|
| `getStatus()` | HTTP 상태코드 (400, 404, 500 등) — 원본 보존 |
| `getCode()` | 에러 코드 (ApiError.code) |
| `getMessage()` | 에러 메시지 |
| `getDescription()` | 추가 설명 |
| `getData()` | 추가 데이터 (validation 필드 목록 등) |
| `getRawResponseBody()` | 원본 응답 바디 JSON 전체 (`@JsonIgnore` — 외부 노출 안됨) |
| `getRemoteServiceName()` | 원격 서비스명 (`@JsonIgnore` — 외부 노출 안됨) |

### 기본 사용 (Axim 프레임워크끼리)

```java
try {
    User user = userClient.getUser(1L);
} catch (XRestException e) {
    e.getStatus();            // 400
    e.getCode();              // "2001"
    e.getMessage();           // "이미 존재하는 이메일"
    e.getData();              // [{field: "email", errorMessage: "..."}]
    e.getRemoteServiceName(); // "user-service"
}
```

### 외부 API — raw body에서 직접 파싱

ApiError 포맷이 아닌 외부 API 에러도 `getRawResponseBody()`로 원본 확보 후 직접 파싱 가능:

```java
try {
    stripeClient.createCharge(request);
} catch (XRestException e) {
    // 원본 JSON에서 직접 파싱
    StripeError err = objectMapper.readValue(e.getRawResponseBody(), StripeError.class);
    log.error("[{}] Stripe error: {}", e.getRemoteServiceName(), err.getError().getMessage());
}
```

### 서비스별 에러 핸들러 (XErrorResponseHandler)

반복 파싱이 번거로운 서비스에 대해 `XErrorResponseHandler` Bean을 등록하면 자동 매칭됨.
Bean 이름 컨벤션: **`@XRestService(value)` + `-error-handler`**

```java
// @XRestService(value = "blockchain-api") 에 매칭
@Component("blockchain-api-error-handler")
public class BlockchainApiErrorHandler implements XErrorResponseHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public XRestException handle(HttpStatus status, String responseBody) {
        try {
            // Node.js 에러 포맷: { "code": "WALLET_NOT_FOUND", "error": "...", "detail": "..." }
            JsonNode node = objectMapper.readTree(responseBody);

            ApiError apiError = new ApiError();
            apiError.setCode(node.path("code").asText(null));
            apiError.setMessage(node.path("error").asText(null));
            apiError.setDescription(node.path("detail").asText(null));

            return new XRestException(status, apiError, responseBody);
        } catch (Exception e) {
            return null;  // null 반환 -> 기본 핸들러(ApiError 파싱)로 폴백
        }
    }
}
```

`@XRestService` 어노테이션 변경 없음:
```java
@XRestService(value = "blockchain-api", host = "${BLOCKCHAIN_API_HOST}")
public interface BlockchainApiClient { ... }
```

### XWebClient에 에러 핸들러 설정

`XWebClient`는 프로그래밍 방식으로 핸들러 설정 (체이닝 지원):

```java
XWebClient client = webClientFactory.create("https://api.stripe.com")
    .errorHandler(new StripeErrorHandler());

// 또는 인라인 람다
XWebClient client = webClientFactory.create("https://api.external.com")
    .errorHandler((status, body) -> {
        JsonNode node = objectMapper.readTree(body);
        ApiError error = new ApiError();
        error.setCode(node.path("error").asText());
        error.setMessage(node.path("error_description").asText());
        return new XRestException(status, error, body);
    });
```

### 에러 핸들러 동작 순서

1. 커스텀 `XErrorResponseHandler` Bean 있음? -> 커스텀 핸들러 실행
2. 커스텀 핸들러가 `null` 반환? -> 기본 핸들러(ApiError 파싱)로 폴백
3. 커스텀 핸들러 Bean 없음? -> 기본 핸들러 직접 실행

### 주의사항

- `rawResponseBody`와 `remoteServiceName`은 `@JsonIgnore`로 외부 클라이언트에 노출되지 않음. 서버 측 로그/디버깅 전용.
- 핸들러 Bean은 `getOrCreateClient()` 의 `computeIfAbsent`에서 한 번만 조회됨 — 매 요청마다 Bean 조회 없음.
- 핸들러가 없어도 `getRawResponseBody()`로 어떤 포맷이든 직접 파싱 가능.

## Session / Token Authentication

The framework provides a built-in token system. **NOT JWT** — uses custom `Base64(payload).HmacSHA256(signature)` format.

### Custom Session Data

```java
@Data
public class UserSession extends SessionData {
    /** User ID */
    private Long userId;
    /** User name */
    private String userName;
    /** User roles (e.g., ["ADMIN", "USER"]) */
    private List<String> roles;
}
```

`SessionData` base fields (auto-managed): `sessionId`, `createDate` (format: `yyyyMMddHHmmss`)

### Generating Tokens (Login)

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final XAccessTokenParseHandler tokenHandler;

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody LoginRequest request) {
        // Authenticate user...
        UserSession session = new UserSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(authenticatedUser.getId());
        session.setUserName(authenticatedUser.getName());
        session.setRoles(authenticatedUser.getRoles());

        String token = tokenHandler.generateAccessToken(session);
        return Map.of("accessToken", token);
    }
}
```

### Using Session in Controllers

Session auto-resolved from `Access-Token` HTTP header. **No annotation required** — auto-detected by parameter type.

```java
@GetMapping("/me")
public UserProfile getMyProfile(UserSession session) {
    // If token missing  -> 401 (NOT_FOUND_ACCESS_TOKEN)
    // If token invalid  -> 401 (INVALID_ACCESS_TOKEN)
    // If token expired  -> 401 (EXPIRE_ACCESS_TOKEN)
    return userService.getProfile(session.getUserId());
}
```

- Requires `XAccessTokenParseHandler` bean (auto-configured or custom `@Component`)
- If `XAccessTokenParseHandler` not registered -> returns `null` (no error)

### Session Configuration

```properties
# MUST be set in production — without it, tokens can be forged
axim.rest.session.secret-key=your-secret-key
axim.rest.session.token-expire-days=90         # Expiration in days (default: 90)
```

### JSON Date Format

The framework ObjectMapper uses `yyyy-MM-dd HH:mm:ss` (NOT ISO 8601):
```java
// O "2024-01-15 14:30:00"
// X "2024-01-15T14:30:00Z"
```
