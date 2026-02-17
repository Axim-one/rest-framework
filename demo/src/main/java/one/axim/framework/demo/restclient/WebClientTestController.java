package one.axim.framework.demo.restclient;

import one.axim.framework.demo.user.User;
import one.axim.framework.rest.proxy.XWebClient;
import one.axim.framework.rest.proxy.XWebClientFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/test/web-client")
public class WebClientTestController {

    private final XWebClient webClient;
    private final XWebClient demoServiceClient;

    public WebClientTestController(XWebClientFactory factory,
                                    @Qualifier("demo-service") XWebClient demoServiceClient) {
        this.webClient = factory.create("http://localhost:8080");
        this.demoServiceClient = demoServiceClient;
    }

    // ──────────────────────────────────────────
    // Normal operations
    // ──────────────────────────────────────────

    @GetMapping("/user/{id}")
    public User getUser(@PathVariable Integer id) {
        return webClient.get("/users/{id}", User.class, id);
    }

    @PostMapping("/user")
    public User createUser(@RequestBody User user) {
        return webClient.post("/users", user, User.class);
    }

    @GetMapping("/users")
    public List<User> getUsers() {
        return webClient.get("/users", new ParameterizedTypeReference<>() {});
    }

    @GetMapping("/user-with-header/{id}")
    public User getUserWithHeader(@PathVariable Integer id) {
        return webClient.spec()
                .get("/users/{id}", id)
                .header("X-Custom-Header", "test-value")
                .retrieve(User.class);
    }

    // ──────────────────────────────────────────
    // @Qualifier (Properties-based) test
    // ──────────────────────────────────────────

    @GetMapping("/qualifier/user/{id}")
    public User getUserByQualifier(@PathVariable Integer id) {
        return demoServiceClient.get("/users/{id}", User.class, id);
    }

    // ──────────────────────────────────────────
    // Error scenario tests
    // ──────────────────────────────────────────

    @GetMapping("/error/not-found")
    public User getNonExistentUser() {
        return webClient.get("/users/{id}", User.class, 99999);
    }

    @PostMapping("/error/duplicate")
    public User createDuplicateUser() {
        User user = new User();
        user.setName("Duplicate Test");
        user.setEmail("hong@example.com");
        return webClient.post("/users", user, User.class);
    }

    @GetMapping("/error/type-mismatch")
    public User typeMismatch() {
        return webClient.get("/users/{id}", User.class, "abc");
    }

    @GetMapping("/error/no-api")
    public User nonExistentApi() {
        return webClient.get("/not-exist-api", User.class);
    }
}
