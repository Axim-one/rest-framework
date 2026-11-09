package one.axim.framework.mybatis.it;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

final class ItContainers {

    private ItContainers() {
    }

    static PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    /** schema 지정 테이블용 DB(it_shop)를 만들어야 해서 root 로 접속한다. */
    static MySQLContainer<?> mysql() {
        return new MySQLContainer<>("mysql:8.0").withUsername("root");
    }

    static void register(DynamicPropertyRegistry r, JdbcDatabaseContainer<?> db, String schemaSuffix) {
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
        r.add("spring.sql.init.mode", () -> "always");
        r.add("spring.sql.init.schema-locations", () -> "classpath:it/schema-" + schemaSuffix + ".sql");
        r.add("mybatis.config-location", () -> "classpath:it/mybatis-config.xml");
    }
}
