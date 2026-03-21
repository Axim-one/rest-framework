# Coding Conventions & Common Pitfalls

## Security Warnings

### 1. Session Secret Key — Token Forgery Risk

Without `axim.rest.session.secret-key`, token payload is Base64-decoded without integrity check. **Anyone can forge a valid session token.**

```properties
# DANGEROUS — attacker creates Base64({"userId":1}) -> valid token
# axim.rest.session.secret-key=

# REQUIRED for production
axim.rest.session.secret-key=a-strong-random-secret-key-at-least-32-chars
```

Rules: Always set in production. Minimum 32 chars. Never commit to source control — use environment variables.

### 2. Request Body Logging in Non-prod Profile

`XRequestFilter` logs full request bodies when profile is NOT `prod`. **Passwords and sensitive fields are logged as-is** (no field-level masking). HTTP headers like `Authorization` are masked, but request body fields are NOT.

```properties
# Non-prod -> logs plaintext passwords from login endpoints
spring.profiles.active=dev

# Prod -> disables body logging and stack traces in errors
spring.profiles.active=prod
```

### 3. Demo Credentials

Demo module contains hardcoded DB credentials for local development only. NEVER copy into production config.

## Coding Conventions

### MANDATORY: Document All Member Variables and Enum Items

Every member variable in Entity, DTO, Request, Response, VO classes and every enum item MUST have a detailed Javadoc comment.

#### Entity Example

```java
@Data
@XEntity("orders")
public class Order {

    /** Order unique identifier (Auto Increment) */
    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;

    /**
     * Order number
     * - Format: "ORD-{yyyyMMdd}-{6-digit sequence}"
     * - Example: "ORD-20240115-000001"
     * - UNIQUE constraint applied
     */
    private String orderNo;

    /**
     * Order status
     * - "PENDING": Awaiting payment
     * - "PAID": Payment confirmed
     * - "SHIPPED": In transit
     * - "DELIVERED": Delivered
     * - "CANCELLED": Order cancelled
     * @see OrderStatus
     */
    private String status;

    /**
     * Total order amount (unit: KRW)
     * - Up to 2 decimal places
     * - Non-negative
     * - Example: 15000.00
     */
    private BigDecimal totalAmount;

    /**
     * Orderer ID (users table FK)
     * - NOT NULL
     */
    private Long userId;
}
```

#### DTO / Request Example

```java
@Data
public class OrderCreateRequest {

    /**
     * Product ID list to order
     * - Minimum 1 item required
     * - Example: [1, 2, 3]
     */
    @NotEmpty
    private List<Long> productIds;

    /**
     * Shipping address (full road address)
     * - Example: "Seoul Gangnam-gu Teheran-ro 123 4F"
     * - Max 200 chars
     */
    @NotBlank
    @Size(max = 200)
    private String shippingAddress;

    /**
     * Payment method code
     * - "CARD": Credit/Debit card
     * - "BANK": Bank transfer
     * - "KAKAO": KakaoPay
     * - "NAVER": NaverPay
     */
    @NotBlank
    private String paymentMethod;
}
```

#### Enum Example

```java
public enum OrderStatus {

    /** Awaiting payment — order created but payment not completed */
    PENDING,

    /** Payment confirmed — preparing for shipment */
    PAID,

    /** In transit — handed to courier */
    SHIPPED,

    /** Delivered — recipient received the item */
    DELIVERED,

    /** Cancelled — cancelled by customer or system. Refund required */
    CANCELLED
}
```

#### Comment Rules

| Rule | Description |
|---|---|
| All member variables | Must have `/** */` Javadoc comment |
| Example values | Include concrete examples |
| Format/Pattern | Document format rules |
| Allowed values | List all valid values for string-coded fields |
| Constraints | Note NOT NULL, UNIQUE, max length, range |
| Enum items | Each item must explain the state/meaning |
| FK references | Note the referenced table |
| Units | Specify units for numeric fields |

## Common Pitfalls

### 1. Column Names vs Field Names

Query derivation and `findWhere()` use **Java field names** (camelCase), NOT column names (snake_case).

```java
// WRONG
User findByUser_name(String name);
userRepository.findWhere(Map.of("user_name", "Alice"));

// CORRECT
User findByUserName(String name);
userRepository.findWhere(Map.of("userName", "Alice"));
```

### 2. findBy Return Type Determines Behavior

```java
User findByEmail(String email);       // -> LIMIT 1 (single result)
List<User> findByEmail(String email); // -> no LIMIT (all matches)
```

### 3. update() Overwrites with NULL

```java
User user = new User();
user.setId(1L);
user.setName("Alice");
// email and status are null

userRepository.update(user);  // Sets email=NULL, status=NULL in DB!
userRepository.modify(user);  // Only sets name='Alice', others preserved
```

### 4. ORDER BY / LIMIT in Custom Mapper SQL

XResultInterceptor handles pagination SQL automatically. Never add ORDER BY or LIMIT.

```java
// WRONG
@Select("SELECT * FROM users WHERE status = #{status} ORDER BY created_at DESC LIMIT 20")
XPage<User> findByStatus(XPagination pagination, @Param("status") String status);

// CORRECT — only the base SELECT
@Select("SELECT * FROM users WHERE status = #{status}")
XPage<User> findByStatus(XPagination pagination, @Param("status") String status);
```

### 5. Never Create Custom Pagination Classes

```java
// WRONG
public class PageRequest { int page; int size; }
public class PageResponse<T> { List<T> items; long total; }

// CORRECT — always use framework classes
XPagination pagination = new XPagination();
XPage<User> result = userRepository.findAll(pagination);
```

### 6. Empty Map in findWhere()

```java
userRepository.findWhere(Map.of());                    // Throws exception
userRepository.findAll();                               // Use findAll() instead
userRepository.findWhere(Map.of("status", "ACTIVE"));  // Non-empty map OK
```

### 7. Query Derivation Method Name Parsing

`And` only splits when preceded by lowercase and followed by uppercase.

```java
User findByBrandName(String brandName);                       // -> single field "brandName"
List<User> findByStatusAndName(String status, String name);   // -> two fields "status", "name"

// Parameter count must match parsed field count
List<User> findByStatusAndName(String status);  // WRONG — 2 fields but 1 param
```

### 8. @XDefaultValue(value="X") Silently Ignored

`isDBDefaultUsed` defaults to `true`, so literal values in `value` are ignored unless `isDBDefaultUsed = false`.

```java
// WRONG — "ACTIVE" is IGNORED
@XDefaultValue(value = "ACTIVE")
private String status;

// CORRECT
@XDefaultValue(value = "ACTIVE", isDBDefaultUsed = false)
private String status;
```
