package one.axim.framework.demo.user;

import org.springframework.web.bind.annotation.*;

import one.axim.framework.core.annotation.XPaginationDefault;
import one.axim.framework.core.data.XPage;
import one.axim.framework.core.data.XPagination;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @PostMapping
    public User createUser(@RequestBody User user) {
        userRepository.insert(user);
        return user;
    }

    @GetMapping("/{id}")
    public User getUser(@PathVariable Integer id) {
        return userRepository.findOne(id);
    }

    @GetMapping
    public XPage<User> getAllUsers(@XPaginationDefault XPagination pagination, @RequestParam(value = "keyword", required = false) String keyword) {
        if (keyword != null) {
            return userMapper.searchUser(pagination, keyword, User.class);
        }
        return userRepository.findAll(pagination);
    }

    @GetMapping("/email/{email}")
    public User getUserByEmail(@PathVariable String email) {
        return userRepository.findByEmail(email);
    }
}