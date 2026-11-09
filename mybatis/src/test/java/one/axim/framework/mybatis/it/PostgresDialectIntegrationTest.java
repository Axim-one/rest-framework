package one.axim.framework.mybatis.it;

import one.axim.framework.mybatis.dialect.XDialect;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL + 명시 설정 {@code axim.mybatis.dialect=postgresql}. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresDialectIntegrationTest extends AbstractDialectIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> PG = ItContainers.postgres();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        ItContainers.register(r, PG, "postgresql");
        r.add("axim.mybatis.dialect", () -> "postgresql");
    }

    @Override
    XDialect expectedDialect() {
        return XDialect.POSTGRESQL;
    }
}
