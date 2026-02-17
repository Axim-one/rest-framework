package one.axim.framework.demo.restclient;

import one.axim.framework.demo.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/test/rest-client")
public class RestClientTestController {

    private final UserServiceClient userServiceClient;

    // ──────────────────────────────────────────
    // Normal operations
    // ──────────────────────────────────────────

    @GetMapping("/user/{id}")
    public User getUser(@PathVariable Integer id) {
        return userServiceClient.getUser(id);
    }

    @PostMapping("/user")
    public User createUser(@RequestBody User user) {
        return userServiceClient.createUser(user);
    }

    @GetMapping("/user/email/{email}")
    public User getUserByEmail(@PathVariable String email) {
        return userServiceClient.getUserByEmail(email);
    }

    // ──────────────────────────────────────────
    // Error scenario tests
    // ──────────────────────────────────────────

    /**
     * Test 1: Non-existent user → null response
     * Server returns 200 with empty body → proxy returns null
     */
    @GetMapping("/error/not-found")
    public User getNonExistentUser() {
        return userServiceClient.getUser(99999);
    }

    /**
     * Test 2: Duplicate email → 500 error propagation
     * Server throws DuplicateKeyException → XExceptionHandler → 500 ApiError
     * → XRestClient catches → XRestException → XExceptionHandler → 500 to caller
     */
    @PostMapping("/error/duplicate")
    public User createDuplicateUser() {
        User user = new User();
        user.setName("Duplicate Test");
        user.setEmail("hong@example.com"); // already exists in DB
        return userServiceClient.createUser(user);
    }

    /**
     * Test 3: Method not allowed → 405 propagation
     * Server has no PUT /users/{id} → 405 Method Not Allowed
     * → XRestClient catches → XRestException → 405 to caller
     */
    @PutMapping("/error/method-not-allowed/{id}")
    public User methodNotAllowed(@PathVariable Integer id, @RequestBody User user) {
        return userServiceClient.updateUser(id, user);
    }

    /**
     * Test 4: Type mismatch → 400 propagation
     * Server expects Integer id but receives "abc" → MethodArgumentTypeMismatchException
     * → XExceptionHandler → 400 ApiError → XRestException → 400 to caller
     */
    @GetMapping("/error/type-mismatch")
    public User typeMismatch() {
        return userServiceClient.getUserByStringId("abc");
    }

    /**
     * Test 5: Non-existent API path → 404 propagation
     * Server has no /not-exist-api handler → 404
     * → XRestClient catches → XRestException → 404 to caller
     */
    @GetMapping("/error/no-api")
    public User nonExistentApi() {
        return userServiceClient.callNonExistentApi();
    }
}
