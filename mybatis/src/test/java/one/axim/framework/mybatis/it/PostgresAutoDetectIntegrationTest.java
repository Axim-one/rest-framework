package one.axim.framework.mybatis.it;

import one.axim.framework.mybatis.dialect.XDialect;
import one.axim.framework.mybatis.dialect.XDialectHolder;
import one.axim.framework.mybatis.it.entity.ItUser;
import one.axim.framework.mybatis.it.repo.ItUserRepository;
import org.junit.jupiter.api.AfterEach;
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

/** PostgreSQL + 설정 없음 → DataSource 로 자동 감지 / 첫 쿼리 감지. */
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

    @AfterEach
    void restore() {
        XDialectHolder.set(XDialect.POSTGRESQL);
    }

    @Test
    @DisplayName("설정 없이 기동하면 DataSource 메타데이터로 POSTGRESQL 감지")
    void detectedAtStartup() {
        assertTrue(XDialectHolder.isResolved());
        assertEquals(XDialect.POSTGRESQL, XDialectHolder.current());
        Long id = users.insert(ItUser.of("auto@x.io", "auto"));
        assertNotNull(id);
        assertEquals("auto", users.findOne(id).getName());
    }

    @Test
    @DisplayName("기동 시 감지 실패(미결정) → 첫 쿼리에서 인터셉터가 커넥션으로 감지")
    void detectedOnFirstQuery() {
        XDialectHolder.reset();
        assertFalse(XDialectHolder.isResolved());

        Long id = users.insert(ItUser.of("lazy@x.io", "lazy"));

        assertEquals(XDialect.POSTGRESQL, XDialectHolder.current());
        assertNotNull(id, "insert 자체가 PG 키 회수 경로를 탔다");
        assertEquals(1, users.count());
    }
}
