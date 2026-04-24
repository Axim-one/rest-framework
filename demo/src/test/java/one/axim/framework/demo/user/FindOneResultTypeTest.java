package one.axim.framework.demo.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression coverage for the {@code XResultInterceptor} result-type swap fix.
 *
 * <p>The bug: {@code IXRepository.findOne(K)} on an entity whose single PK is
 * <em>not</em> auto-increment could return the scalar PK value instead of the
 * entity, producing a {@code ClassCastException} at the call site when MyBatis
 * warm-path caching caused {@code ResultMap.getType()} to be polluted to
 * {@code Long.class}/{@code Integer.class}.
 *
 * <p>These tests exercise both the bug-prone path (non-AI PK) and the previously
 * healthy paths (AI PK, count) in a sequence designed to warm MyBatis caches.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
public class FindOneResultTypeTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    void findOne_returnsEntity_whenPrimaryKeyIsNotAutoIncrement() {
        User user = new User();
        user.setName("profile-owner");
        user.setEmail("profile-owner@example.com");
        userRepository.save(user);
        Integer userId = user.getId();
        assertNotNull(userId);

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setNickname("Owner");
        profile.setBio("Regression fixture");
        userProfileRepository.save(profile);

        // Warm-path cache trigger: a scalar-returning SELECT (count) on the same
        // table exercises the MyBatis MappedStatement cache in a way that, before
        // the fix, could pollute findOne's ResultMap type to Long.class.
        userProfileRepository.count();

        UserProfile found = userProfileRepository.findOne(userId);

        assertNotNull(found);
        assertInstanceOf(UserProfile.class, found,
                "findOne must return the entity, not the raw PK — see XResultInterceptor bug fix");
        assertEquals(userId, found.getUserId());
        assertEquals("Owner", found.getNickname());
    }

    @Test
    void findOne_returnsEntity_whenPrimaryKeyIsAutoIncrement() {
        User user = new User();
        user.setName("ai-pk-user");
        user.setEmail("ai-pk-user@example.com");
        userRepository.save(user);

        User found = userRepository.findOne(user.getId());

        assertNotNull(found);
        assertInstanceOf(User.class, found);
        assertEquals(user.getId(), found.getId());
        assertEquals("ai-pk-user", found.getName());
    }

    @Test
    void count_returnsScalarLong_afterInterceptorChange() {
        // Guards against a failure mode introduced if the interceptor were to
        // over-eagerly swap the ResultMap for scalar-returning methods like count.
        long before = userProfileRepository.count();

        User user = new User();
        user.setName("count-user");
        user.setEmail("count-user@example.com");
        userRepository.save(user);

        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setNickname("Counted");
        userProfileRepository.save(profile);

        long after = userProfileRepository.count();
        assertEquals(before + 1, after);
    }
}
