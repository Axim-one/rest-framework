package one.axim.framework.mybatis.it;

import one.axim.framework.mybatis.dialect.XDialect;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** MySQL + 설정 없음(자동 감지). 1.4.x 소비자와 같은 조건. */
@Testcontainers(disabledWithoutDocker = true)
class MysqlDialectIntegrationTest extends AbstractDialectIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = ItContainers.mysql();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        ItContainers.register(r, MYSQL, "mysql");
    }

    @Override
    XDialect expectedDialect() {
        return XDialect.MYSQL;
    }
}
