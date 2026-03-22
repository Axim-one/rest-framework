# REST Client & Authentication Reference

## REST Client Strategy: @XRestService vs XWebClient

| Situation | Use | Reason |
|---|---|---|
| Internal microservice (fixed API contract) | `@XRestService` | Declarative, type-safe, XPagination auto-conversion |
| External API / dynamic URL | `XWebClient` | Programmatic, flexible URL, custom headers |
| File upload / Multipart | `XWebClient` (spec builder) | @XRestService only supports JSON body |
| Service-to-service token forwarding | `@XRestService` + `@RequestHeader` | Declarative header passing |
| One-off or test calls | `XWebClientFactory.create(url)` | No Bean registration needed |

**CRITICAL:** Both clients share the same `RestTemplate`/`RestClient` connection pool configured via `axim.rest.client.*` properties.

---

## Setup Requirements

### Application Class

```java
@ComponentScan({"one.axim.framework.rest", "one.axim.framework.mybatis", "com.myapp"})
@SpringBootApplication
@XRestServiceScan("com.myapp.client")  // REQUIRED for @XRestService
public class MyApplication { ... }
```

### Configuration Properties

```properties
# Connection pool (shared by XRestClient and XWebClient)
axim.rest.client.pool-size=200                    # Max connections (default: 200)
axim.rest.client.connection-request-timeout=30    # seconds (default: 30)
axim.rest.client.response-timeout=30              # seconds (default: 30)

# Debug logging (request/response URLs and bodies)
axim.rest.debug=false                             # default: false

# Gateway mode (for @XRestService without host)
axim.rest.gateway.host=http://api-gateway:8080

# XWebClient named beans
axim.web-client.services.userClient=http://user-service:8080
axim.web-client.services.orderClient=http://order-service:8080
```

---

## @XRestService (Declarative REST Client)

### Annotation Reference

#### @XRestService

| Attribute | Default | Description |
|---|---|---|
| `value` | `""` | Service name — Bean identifier, gateway routing path, error handler matching key |
| `host` | `""` | Direct host URL (supports `${}` placeholders). Empty = gateway mode |
| `version` | `""` | API version (gateway mode only, becomes URL path segment) |

#### @XRestAPI

| Attribute | Default | Description |
|---|---|---|
| `value` | `""` | API path (supports `{variable}` placeholders) |
| `method` | `GET` | HTTP method: `GET`, `POST`, `PUT`, `DELETE`, `PATCH` |

#### @XRestServiceScan

| Attribute | Description |
|---|---|
| `value` | Package(s) to scan for `@XRestService` interfaces |

### URL Construction

```
Direct Mode  (host specified):   {host}{@XRestAPI.value}
Gateway Mode (host empty):       {gatewayHost}/{serviceName}/{version}{@XRestAPI.value}
```

```java
// Direct: http://localhost:8081/users/1
@XRestService(value = "user-service", host = "http://localhost:8081")
// -> GET http://localhost:8081/users/1

// Direct with env variable: ${USER_SERVICE_HOST}/users/1
@XRestService(value = "user-service", host = "${USER_SERVICE_HOST:http://localhost:8081}")

// Gateway: http://api-gateway:8080/user-service/v1/users/1
@XRestService(value = "user-service", version = "v1")
// -> requires axim.rest.gateway.host=http://api-gateway:8080
```

### Parameter Annotations

Supports Spring's standard annotations on method parameters:

| Annotation | Description | Example |
|---|---|---|
| `@PathVariable("name")` | URL path variable | `/users/{id}` -> `@PathVariable("id") Long id` |
| `@RequestBody` | JSON request body | `@RequestBody UserCreateRequest request` |
| `@RequestParam("name")` | Query parameter | `?status=ACTIVE` -> `@RequestParam("status") String status` |
| `@RequestHeader("name")` | HTTP header | `@RequestHeader("Access-Token") String token` |
| `XPagination` (no annotation) | Auto-converted to query params | `?page=1&size=20&sort=createdAt,DESC` |

```java
@XRestService(value = "order-service", host = "${ORDER_SERVICE_HOST}")
public interface OrderServiceClient {

    // GET /orders/123
    @XRestAPI(value = "/orders/{id}", method = XHttpMethod.GET)
    Order getOrder(@PathVariable("id") Long id);

    // POST /orders (JSON body)
    @XRestAPI(value = "/orders", method = XHttpMethod.POST)
    Order createOrder(@RequestBody OrderCreateRequest request);

    // GET /orders?status=ACTIVE&keyword=test
    @XRestAPI(value = "/orders", method = XHttpMethod.GET)
    List<Order> search(@RequestParam("status") String status,
                       @RequestParam("keyword") String keyword);

    // GET /orders with custom header
    @XRestAPI(value = "/orders", method = XHttpMethod.GET)
    List<Order> getOrders(@RequestHeader("X-Tenant-Id") String tenantId);

    // GET /orders?page=1&size=20&sort=createdAt,DESC (XPagination auto-conversion)
    @XRestAPI(value = "/orders", method = XHttpMethod.GET)
    XPage<Order> listOrders(XPagination pagination);

    // PUT /orders/123 with body + header (combined)
    @XRestAPI(value = "/orders/{id}", method = XHttpMethod.PUT)
    Order updateOrder(@PathVariable("id") Long id,
                      @RequestBody OrderUpdateRequest request,
                      @RequestHeader("Access-Token") String token);
}
```

### Return Type Handling

| Return Type | Behavior |
|---|---|
| `T` (simple class) | Deserialized via `ObjectMapper.readValue(body, T.class)` |
| `List<T>`, `XPage<T>` (generic) | Deserialized via `ParameterizedTypeReference` — preserves generic type |
| `String` | Raw response body returned as-is (no JSON parsing) |
| `void` / `Void` | Response body ignored |
| `null` response (2xx) | Returns `null` — no exception thrown |

### Header Forwarding Pattern (Service-to-Service)

```java
// Service A -> Service B: forward the caller's access token
@RestController
@RequiredArgsConstructor
public class OrderController {
    private final OrderServiceClient orderClient;

    @GetMapping("/my-orders")
    public List<Order> getMyOrders(UserSession session,
                                   @RequestHeader("Access-Token") String token) {
        // Forward token to downstream service
        return orderClient.getMyOrders(session.getUserId(), token);
    }
}

// OrderServiceClient
@XRestService(value = "order-service", host = "${ORDER_SERVICE_HOST}")
public interface OrderServiceClient {
    @XRestAPI(value = "/orders", method = XHttpMethod.GET)
    List<Order> getMyOrders(@RequestParam("userId") Long userId,
                            @RequestHeader("Access-Token") String token);
}
```

---

## XWebClient (Programmatic REST Client)

### Bean Registration

#### Option 1: Properties-based (Named beans)

```properties
# application.properties — each entry creates a named XWebClient bean
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

#### Option 2: Factory (Dynamic URL)

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

> `XWebClientFactory.create()` is **cached** by base URL via `ConcurrentHashMap` — safe to call repeatedly.

### API Reference

```java
// === Simple API (Class<T>) ===
client.get("/users/{id}", User.class, id);
client.post("/users", body, User.class);
client.put("/users/{id}", body, User.class, id);
client.patch("/users/{id}", body, User.class, id);
client.delete("/users/{id}", Void.class, id);

// === Generic types (ParameterizedTypeReference<T>) ===
client.get("/users", new ParameterizedTypeReference<List<User>>() {});
client.post("/users/batch", bodyList, new ParameterizedTypeReference<List<User>>() {});

// === Builder API (custom headers, complex requests) ===
client.spec()
        .get("/users?keyword=" + keyword)
        .header("X-API-Key", "my-key")
        .header("Accept-Language", "ko-KR")
        .retrieve(new ParameterizedTypeReference<List<User>>() {});

client.spec()
        .post("/orders")
        .header("Authorization", "Bearer " + token)
        .body(orderRequest)
        .retrieve(Order.class);

// === Error handler chaining ===
XWebClient client = webClientFactory.create("https://api.stripe.com")
        .errorHandler(new StripeErrorHandler());
```

### @XRestService vs XWebClient Comparison

| Feature | `@XRestService` | `XWebClient` |
|---|---|---|
| Style | Interface + annotations (declarative) | Direct method calls (programmatic) |
| Bean creation | `@XRestServiceScan` | Properties or `XWebClientFactory` |
| Best for | Internal microservice calls | External API, dynamic URLs |
| Pagination | Auto `XPagination` -> query params | Manual query string |
| Custom headers | `@RequestHeader` per method | `.spec().header()` or global |
| Error handler | Bean convention (`-error-handler`) | `.errorHandler()` chaining |
| URL construction | Automatic (host + path) | Manual (base URL + path) |
| Connection pool | Shared `RestTemplate` | Shared `RestClient` |

---

## Error Propagation

`@XRestService` / `XWebClient` call returns error -> framework auto-preserves all info in `XRestException`.

### Flow

```
[Remote Service]                           [Caller]

Error -> XExceptionHandler                 XRestClient/XWebClient receives
  -> ApiError JSON response                  -> ApiError parsing
  { code, message, description, data }       -> XRestException created
                                               (code, message, description, data preserved)
                                               (rawResponseBody = original JSON)
                                               (remoteServiceName = @XRestService value)
                                             -> caller can catch
```

### XRestException Error Fields

| Field | Description |
|---|---|
| `getStatus()` | HTTP status code (400, 404, 500, etc.) — preserved from origin |
| `getCode()` | Error code (ApiError.code) |
| `getMessage()` | Error message |
| `getDescription()` | Additional description |
| `getData()` | Additional data (validation field list, etc.) |
| `getRawResponseBody()` | Original response body JSON (`@JsonIgnore` — not exposed to external clients) |
| `getRemoteServiceName()` | Remote service name (`@JsonIgnore` — not exposed to external clients) |

### Basic Usage (Axim Framework to Framework)

```java
try {
    User user = userClient.getUser(1L);
} catch (XRestException e) {
    e.getStatus();            // 400
    e.getCode();              // "2001"
    e.getMessage();           // "Email already exists"
    e.getData();              // [{field: "email", errorMessage: "..."}]
    e.getRemoteServiceName(); // "user-service"
}
```

### External API — Parse raw body directly

```java
try {
    stripeClient.createCharge(request);
} catch (XRestException e) {
    StripeError err = objectMapper.readValue(e.getRawResponseBody(), StripeError.class);
    log.error("[{}] Stripe error: {}", e.getRemoteServiceName(), err.getError().getMessage());
}
```

### Per-Service Error Handler (XErrorResponseHandler)

Register a Bean to auto-match by convention: **`@XRestService(value)` + `-error-handler`**

```java
@Component("blockchain-api-error-handler")
public class BlockchainApiErrorHandler implements XErrorResponseHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public XRestException handle(HttpStatus status, String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            ApiError apiError = new ApiError();
            apiError.setCode(node.path("code").asText(null));
            apiError.setMessage(node.path("error").asText(null));
            apiError.setDescription(node.path("detail").asText(null));
            return new XRestException(status, apiError, responseBody);
        } catch (Exception e) {
            return null;  // null -> fallback to default handler
        }
    }
}
```

XWebClient uses chaining:
```java
XWebClient client = webClientFactory.create("https://api.external.com")
    .errorHandler((status, body) -> {
        JsonNode node = objectMapper.readTree(body);
        ApiError error = new ApiError();
        error.setCode(node.path("error").asText());
        error.setMessage(node.path("error_description").asText());
        return new XRestException(status, error, body);
    });
```

### Handler Execution Order

1. Custom `XErrorResponseHandler` Bean exists? -> execute custom handler
2. Custom handler returns `null`? -> fallback to default handler (ApiError parsing)
3. No custom handler Bean? -> default handler directly

---

## REST Client Common Pitfalls

### 1. Missing @XRestServiceScan

```java
// WRONG — @XRestService beans not created, injection fails
@SpringBootApplication
public class MyApplication { ... }

// CORRECT
@SpringBootApplication
@XRestServiceScan("com.myapp.client")
public class MyApplication { ... }
```

### 2. @PathVariable value must match exactly

```java
// WRONG — variable name mismatch
@XRestAPI(value = "/users/{id}", method = XHttpMethod.GET)
User getUser(@PathVariable("userId") Long id);  // "userId" != "id"

// CORRECT
@XRestAPI(value = "/users/{id}", method = XHttpMethod.GET)
User getUser(@PathVariable("id") Long id);
```

### 3. @RequestParam creates query string, NOT form data

```java
// This creates: GET /orders?status=ACTIVE (query string)
@XRestAPI(value = "/orders", method = XHttpMethod.GET)
List<Order> search(@RequestParam("status") String status);

// For form data, use @RequestBody with Map
@XRestAPI(value = "/auth/token", method = XHttpMethod.POST)
TokenResponse getToken(@RequestBody Map<String, String> formData);
```

### 4. XPagination parameter needs no annotation

```java
// WRONG — don't annotate XPagination
@XRestAPI(value = "/users", method = XHttpMethod.GET)
XPage<User> list(@RequestParam("pagination") XPagination pagination);

// CORRECT — framework auto-detects and converts
@XRestAPI(value = "/users", method = XHttpMethod.GET)
XPage<User> list(XPagination pagination);
// -> ?page=1&size=20&sort=createdAt,DESC (auto-converted)
```

### 5. Direct mode host with env variable

```java
// WRONG — literal string, not resolved
@XRestService(value = "user-service", host = "USER_SERVICE_HOST")

// CORRECT — Spring placeholder syntax
@XRestService(value = "user-service", host = "${USER_SERVICE_HOST:http://localhost:8081}")
```

### 6. @XRestAPI missing on method

```java
@XRestService(value = "user-service", host = "...")
public interface UserServiceClient {
    // WRONG — no @XRestAPI, returns null silently
    User getUser(Long id);

    // CORRECT
    @XRestAPI(value = "/users/{id}", method = XHttpMethod.GET)
    User getUser(@PathVariable("id") Long id);
}
```

### 7. Debug mode logs all request/response

```properties
# Enable for development only — logs all URLs and response bodies
axim.rest.debug=true
```

This adds `INFO` level logs:
```
GET request url :: http://user-service:8080/users/1
response status : 200 body :: {"id":1,"name":"Alice",...}
```

---

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
