package one.axim.framework.demo.restclient;

import one.axim.framework.demo.user.User;
import one.axim.framework.rest.annotation.XHttpMethod;
import one.axim.framework.rest.annotation.XRestAPI;
import one.axim.framework.rest.annotation.XRestService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@XRestService(value = "demo-self", host = "http://localhost:8080")
public interface UserServiceClient {

    // --- Normal operations ---

    @XRestAPI(value = "/users/{id}", method = XHttpMethod.GET)
    User getUser(@PathVariable("id") Integer id);

    @XRestAPI(value = "/users", method = XHttpMethod.POST)
    User createUser(@RequestBody User user);

    @XRestAPI(value = "/users/email/{email}", method = XHttpMethod.GET)
    User getUserByEmail(@PathVariable("email") String email);

    // --- Error scenario operations ---

    // PUT /users/{id} is not defined on server → 405 Method Not Allowed
    @XRestAPI(value = "/users/{id}", method = XHttpMethod.PUT)
    User updateUser(@PathVariable("id") Integer id, @RequestBody User user);

    // GET /users/{id} with String id → type mismatch on server → 400
    @XRestAPI(value = "/users/{id}", method = XHttpMethod.GET)
    User getUserByStringId(@PathVariable("id") String id);

    // GET /not-exist-api → no handler on server → 404
    @XRestAPI(value = "/not-exist-api", method = XHttpMethod.GET)
    User callNonExistentApi();
}
