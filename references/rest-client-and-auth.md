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
