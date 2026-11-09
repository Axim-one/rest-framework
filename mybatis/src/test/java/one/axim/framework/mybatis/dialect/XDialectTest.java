package one.axim.framework.mybatis.dialect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XDialectTest {

    @AfterEach
    void reset() {
        XDialectHolder.reset();
    }

    @Test
    @DisplayName("설정값 해석: 별칭 허용, 대소문자 무시, 모르는 값은 기동 실패")
    void fromName() {
        assertEquals(XDialect.MYSQL, XDialect.fromName("mysql"));
        assertEquals(XDialect.MYSQL, XDialect.fromName("MariaDB"));
        assertEquals(XDialect.POSTGRESQL, XDialect.fromName(" PostgreSQL "));
        assertEquals(XDialect.POSTGRESQL, XDialect.fromName("postgres"));
        assertEquals(XDialect.POSTGRESQL, XDialect.fromName("pg"));
        assertThrows(IllegalArgumentException.class, () -> XDialect.fromName("oracle"));
    }

    @Test
    @DisplayName("제품명 감지")
    void fromProductName() {
        assertEquals(XDialect.MYSQL, XDialect.fromProductName("MySQL"));
        assertEquals(XDialect.MYSQL, XDialect.fromProductName("MariaDB"));
        assertEquals(XDialect.POSTGRESQL, XDialect.fromProductName("PostgreSQL"));
        assertNull(XDialect.fromProductName("H2"));
        assertNull(XDialect.fromProductName(null));
    }

    @Test
    @DisplayName("방언별 SQL 조각")
    void fragments() {
        assertEquals("`a`.`b`", XDialect.MYSQL.qualifiedTableName("a", "b"));
        assertEquals("\"a\".\"b\"", XDialect.POSTGRESQL.qualifiedTableName("a", "b"));
        assertEquals("\"b\"", XDialect.POSTGRESQL.qualifiedTableName(null, "b"));

        assertEquals(" LIMIT 20", XDialect.POSTGRESQL.limit(20, 0));
        assertEquals(" LIMIT 20 OFFSET 40", XDialect.POSTGRESQL.limit(20, 40));
        assertEquals(" LIMIT 40, 20", XDialect.MYSQL.limit(20, 40));

        assertEquals(" ON CONFLICT (order_id, item_no) DO UPDATE SET qty = #{model.qty}",
                XDialect.POSTGRESQL.upsertClause(List.of("order_id", "item_no"), "qty = #{model.qty}"));
        assertEquals(" ON CONFLICT (id) DO NOTHING", XDialect.POSTGRESQL.upsertClause(List.of("id"), ""));
        assertEquals(" ON DUPLICATE KEY UPDATE qty = #{model.qty}",
                XDialect.MYSQL.upsertClause(List.of("id"), "qty = #{model.qty}"));
    }

    @Test
    @DisplayName("미결정 상태: current() 는 MySQL 이지만 결정으로 고정하지 않는다")
    void holderDefaults() {
        assertFalse(XDialectHolder.isResolved());
        assertEquals(XDialect.MYSQL, XDialectHolder.current());
        assertEquals(XDialect.POSTGRESQL, XDialectHolder.resolveIfAbsent(() -> XDialect.POSTGRESQL));
        assertEquals(XDialect.POSTGRESQL, XDialectHolder.resolveIfAbsent(() -> XDialect.MYSQL), "이미 정해졌으면 유지");
        XDialectHolder.reset();
        assertEquals(XDialect.MYSQL, XDialectHolder.resolveIfAbsent(() -> null), "감지 실패 → MySQL 로 고정");
        assertTrue(XDialectHolder.isResolved());
    }

    @Test
    @DisplayName("XDialectConfig: 명시 설정이 자동 감지보다 우선")
    void explicitWinsOverDetection() {
        new XDialectConfig(new MockEnvironment().withProperty("axim.mybatis.dialect", "postgresql"),
                provider(dataSource("MySQL")));
        assertEquals(XDialect.POSTGRESQL, XDialectHolder.current());
    }

    @Test
    @DisplayName("XDialectConfig: 설정 없으면 DataSource 메타데이터로 감지")
    void autoDetect() {
        new XDialectConfig(new MockEnvironment(), provider(dataSource("PostgreSQL")));
        assertTrue(XDialectHolder.isResolved());
        assertEquals(XDialect.POSTGRESQL, XDialectHolder.current());
    }

    @Test
    @DisplayName("XDialectConfig: 감지 실패(DB 다운 등)면 미결정으로 두고 current() 는 MySQL")
    void detectionFailureLeavesUnresolved() {
        new XDialectConfig(new MockEnvironment(), provider(failingDataSource()));
        assertFalse(XDialectHolder.isResolved());
        assertEquals(XDialect.MYSQL, XDialectHolder.current());

        new XDialectConfig(new MockEnvironment(), provider(null));
        assertFalse(XDialectHolder.isResolved());
    }

    @Test
    @DisplayName("XDialectConfig: 잘못된 설정값은 기동 실패")
    void invalidPropertyFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new XDialectConfig(
                new MockEnvironment().withProperty("axim.mybatis.dialect", "postgre"), provider(null)));
    }

    // ── stubs ──

    private static org.springframework.beans.factory.ObjectProvider<DataSource> provider(DataSource ds) {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        if (ds != null) {
            bf.registerSingleton("dataSource", ds);
        }
        return bf.getBeanProvider(DataSource.class);
    }

    private static DataSource dataSource(String productName) {
        DatabaseMetaData meta = proxy(DatabaseMetaData.class, (m, a) ->
                m.getName().equals("getDatabaseProductName") ? productName : null);
        Connection conn = proxy(Connection.class, (m, a) -> m.getName().equals("getMetaData") ? meta : null);
        return proxy(DataSource.class, (m, a) -> m.getName().equals("getConnection") ? conn : null);
    }

    private static DataSource failingDataSource() {
        return proxy(DataSource.class, (m, a) -> {
            if (m.getName().equals("getConnection")) {
                throw new SQLException("Connection refused");
            }
            return null;
        });
    }

    interface Handler {
        Object handle(java.lang.reflect.Method m, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler h) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, m, a) -> m.getName().equals("toString") ? type.getSimpleName() : h.handle(m, a));
    }
}
