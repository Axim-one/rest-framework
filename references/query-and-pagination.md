# Query Strategy & Pagination Reference

## Query Strategy Decision Guide

Query derivation only supports exact-match `=` with `And` combinator. It does NOT support LIKE, BETWEEN, OR, IN, >, <, JOIN, subqueries, or aggregation.

| Query Type | Use |
|---|---|
| Exact-match WHERE (`=`, `And`) | `@XRepository` query derivation |
| CRUD (save, update, delete) | `@XRepository` built-in methods |
| LIKE, BETWEEN, range | **`@Mapper`** |
| JOIN (multi-table) | **`@Mapper`** |
| OR, IN, subquery, aggregation | **`@Mapper`** |
| Complex sorting | **`@Mapper`** |

When Repository cannot handle a query, immediately create a `@Mapper` interface.

## Custom Mapper with Pagination

The framework's `XResultInterceptor` automatically handles COUNT, ORDER BY, and LIMIT. Write only the base SELECT.

**Rules:**
1. Include `XPagination` as a parameter
2. Return `XPage<T>` (entity type inferred from generic)
3. Do NOT add ORDER BY or LIMIT in SQL

```java
@Mapper
public interface UserMapper {

    // LIKE search with pagination
    @Select("SELECT * FROM users WHERE name LIKE CONCAT('%', #{keyword}, '%')")
    XPage<User> searchByName(XPagination pagination, @Param("keyword") String keyword);

    // BETWEEN with pagination
    @Select("SELECT * FROM users WHERE created_at BETWEEN #{from} AND #{to}")
    XPage<User> findByDateRange(XPagination pagination,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    // JOIN with pagination
    @Select("SELECT u.*, d.name AS department_name FROM users u " +
            "INNER JOIN departments d ON u.department_id = d.id " +
            "WHERE d.status = #{status}")
    XPage<UserWithDepartment> findUsersWithDepartment(XPagination pagination,
                                                      @Param("status") String status);

    // Multiple conditions (OR, IN)
    @Select("<script>" +
            "SELECT * FROM users WHERE status IN " +
            "<foreach item='s' collection='statuses' open='(' separator=',' close=')'>" +
            "#{s}" +
            "</foreach>" +
            "</script>")
    XPage<User> findByStatuses(XPagination pagination,
                               @Param("statuses") List<String> statuses);

    // Without pagination — return List<T>
    @Select("SELECT * FROM users WHERE email LIKE CONCAT('%', #{keyword}, '%')")
    List<User> searchByEmail(@Param("keyword") String keyword);

    // Aggregation (non-paginated)
    @Select("SELECT department_id, COUNT(*) as user_count FROM users GROUP BY department_id")
    List<Map<String, Object>> countByDepartment();
}
```

## Pagination Details

- Page is **1-indexed** (page=1 is the first page)
- Defaults: `page=1`, `size=20`
- Always use `XPagination` and `XPage` — NEVER create custom pagination classes

```java
XPagination pagination = new XPagination();
pagination.setPage(1);       // 1-based
pagination.setSize(20);
pagination.addOrder(new XOrder("createdAt", XDirection.DESC));

XPage<User> result = userRepository.findAll(pagination);
result.getTotalCount();   // total rows
result.getPage();         // current page
result.getPageRows();     // rows in this page
result.getHasNext();      // more pages?
```

### Controller with Auto-binding

```java
@GetMapping
public XPage<User> searchUsers(@XPaginationDefault XPagination pagination) {
    return userRepository.findAll(pagination);
}
// Accepts: ?page=1&size=10&sort=email,asc
```

### @XPaginationDefault Attributes

| Attribute | Default | Description |
|---|---|---|
| `page` | `1` | Page number (1-based) |
| `size` | `10` | Rows per page |
| `offset` | `0` | Row offset (alternative to page) |
| `column` | `""` (none) | Default sort column (camelCase) |
| `direction` | `DESC` | Default sort direction |

Sort parsing:
```
?sort=createdAt,DESC          -> XOrder("createdAt", DESC)
?sort=name                    -> XOrder("name", ASC)   <- omitted direction defaults to ASC
?sort=createdAt,DESC&sort=name,ASC  -> multi-sort
```

Priority: `?page=` present -> page-based; `?offset=` only -> offset-based. Query params override annotation defaults. `"undefined"` and `"null"` strings are treated as absent.

```java
@GetMapping
public XPage<User> listUsers(
        @XPaginationDefault(size = 20, column = "createdAt", direction = XDirection.DESC)
        XPagination pagination) {
    return userRepository.findAll(pagination);
}
```

## Controller Pattern: Repository + Mapper Together

A typical controller uses Repository for simple operations and Mapper for complex queries:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;  // simple CRUD
    private final UserMapper userMapper;           // complex queries

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userRepository.findOne(id);
    }

    @GetMapping
    public XPage<User> searchUsers(@XPaginationDefault XPagination pagination,
                                   @RequestParam(required = false) String keyword) {
        if (keyword != null) {
            return userMapper.searchByName(pagination, keyword);
        }
        return userRepository.findAll(pagination);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        userRepository.save(user);
        return user;
    }
}
```

## Complete Service Layer Example

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User create(User user) {
        userRepository.save(user);
        return user;
    }

    public User partialUpdate(Long id, UserUpdateRequest req) {
        User user = new User();
        user.setId(id);
        user.setName(req.getName());
        userRepository.modify(user);    // selective UPDATE
        return userRepository.findOne(id);
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
