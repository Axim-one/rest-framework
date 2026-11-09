package one.axim.framework.mybatis.it;

import one.axim.framework.mybatis.dialect.XDialect;
import one.axim.framework.mybatis.dialect.XDialectHolder;
import one.axim.framework.mybatis.it.entity.ItUser;
import one.axim.framework.mybatis.it.repo.ItUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PostgreSQL + 설정 없음 → 첫 쿼리에서 커넥션 메타데이터로 자동 감지. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ItTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PostgresAutoDetectIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = ItContainers.postgres();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        ItContainers.register(r, PG, "postgresql");
    }

    @Autowired
    ItUserRepository users;

    @Test
    @DisplayName("설정 없이 기동: 기동 시점엔 미결정, 첫 쿼리에서 커넥션 메타데이터로 POSTGRESQL 감지")
    void detectedOnFirstQuery() {
        XDialectHolder.reset(); // 같은 클래스의 다른 테스트가 먼저 돌았을 수 있다
        assertFalse(XDialectHolder.isResolved());

        Long id = users.insert(ItUser.of("lazy@x.io", "lazy"));

        assertTrue(XDialectHolder.isResolved());
        assertEquals(XDialect.POSTGRESQL, XDialectHolder.current());
        assertNotNull(id, "첫 쿼리(insert)부터 PG 키 회수 경로를 탔다");
        assertEquals("lazy", users.findOne(id).getName());
        assertEquals(1, users.count());
    }

    @Test
    @DisplayName("감지 후 페이지네이션도 PG LIMIT/OFFSET 으로 동작")
    void paginationAfterDetection() {
        for (int i = 0; i < 3; i++) {
            users.insert(ItUser.of("p" + i + "@x.io", "p" + i));
        }
        assertEquals(XDialect.POSTGRESQL, XDialectHolder.current());
        var page = users.findAll(AbstractDialectIntegrationTest.page(2, 2, "id", one.axim.framework.core.data.XDirection.ASC));
        assertEquals(3, page.getTotalCount());
        assertEquals(1, page.getPageRows().size());
    }
}
