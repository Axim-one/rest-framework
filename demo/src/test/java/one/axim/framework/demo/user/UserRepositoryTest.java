package one.axim.framework.demo.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testSaveAll() {
        User user1 = new User();
        user1.setName("testuser1");
        user1.setEmail("test1@example.com");

        User user2 = new User();
        user2.setName("testuser2");
        user2.setEmail("test2@example.com");

        List<User> users = Arrays.asList(user1, user2);
        userRepository.saveAll(users);

        User foundUser1 = userRepository.findByEmail("test1@example.com");
        assertNotNull(foundUser1);
        assertEquals("testuser1", foundUser1.getName());

        User foundUser2 = userRepository.findByEmail("test2@example.com");
        assertNotNull(foundUser2);
        assertEquals("testuser2", foundUser2.getName());
    }

    @Test
    void testExistsByEmail() {
        User user = new User();
        user.setName("existinguser");
        user.setEmail("exists@example.com");
        userRepository.save(user);

        assertTrue(userRepository.existsByEmail("exists@example.com"));
        assertFalse(userRepository.existsByEmail("nonexistent@example.com"));
    }
}
