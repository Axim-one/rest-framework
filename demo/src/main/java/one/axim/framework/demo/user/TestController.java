package one.axim.framework.demo.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/test")
public class TestController {

    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/saveall")
    public String testSaveAll() {
        User user1 = new User();
        user1.setName("controller_user1");
        user1.setEmail("controller1@example.com");

        User user2 = new User();
        user2.setName("controller_user2");
        user2.setEmail("controller2@example.com");

        List<User> users = Arrays.asList(user1, user2);
        userRepository.saveAll(users);
        return "saveAll successful!";
    }

    @GetMapping("/existsbyemail/{email}")
    public String testExistsByEmail(@PathVariable String email) {
        boolean exists = userRepository.existsByEmail(email);
        return "User with email " + email + " exists: " + exists;
    }

    @PostMapping("/setup_exists_test")
    public String setupExistsTest() {
        User existing = userRepository.findByEmail("exists_controller@example.com");
        if (existing != null) {
            userRepository.delete(existing.getId());
        }

        User user = new User();
        user.setName("existing_user_controller");
        user.setEmail("exists_controller@example.com");
        userRepository.save(user);
        return "Setup complete. User with email exists_controller@example.com created.";
    }

    @PostMapping("/save")
    public User saveUser() {
        User user = new User();
        user.setName("existing_user_controller");
        user.setEmail("exists_controller@example.com");
        userRepository.save(user);

        log.info("save id: {}", user.getId());

        Integer id = userRepository.insert(user);

        log.info("insert id: {}", id);

        return user;
    }
}