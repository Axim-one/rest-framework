package one.axim.framework.mybatis.dialect;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * DB 방언별 SQL 차이를 한 곳에 모은다 (v1.5.0).
 *
 * <p>차이 지점: 식별자 따옴표 · LIMIT · upsert 절 · insert-ignore · 생성 키 회수.
 * 그 외 SQL(컬럼명, WHERE, COUNT 래핑 등)은 두 방언에서 같다 — 컬럼명은 따옴표 없이
 * snake_case 소문자로 나가므로 PostgreSQL 에서도 그대로 맞는다.
 *
 * <p><b>MySQL 출력은 1.4.2 와 문자열 단위로 같아야 한다</b>
 * ({@code CrudSqlSnapshotTest} 가 고정). 기본값도 MySQL 이다.
 *
 * <p>한 애플리케이션 안에서 두 종류의 DB 를 섞어 쓰는 것은 지원하지 않는다 —
 * 방언은 JVM 전역 하나({@link XDialectHolder})다.
 */
public enum XDialect {

    MYSQL {
        @Override
        public String quoteIdentifier(String identifier) {
            return "`" + identifier + "`";
        }

        @Override
        public String limit(int size, int offset) {
            if (offset == 0) {
                return " LIMIT " + size;
            }
            return " LIMIT " + offset + ", " + size;
        }

        @Override
        public String upsertClause(List<String> conflictColumns, String updateSet) {
            return " ON DUPLICATE KEY UPDATE " + updateSet;
        }

        @Override
        public String insertIgnoreInto() {
            return "INSERT IGNORE INTO ";
        }

        @Override
        public String insertIgnoreSuffix() {
            return "";
        }

        @Override
        public boolean usesJdbcGeneratedKeys() {
            return false;
        }

        @Override
        public boolean upsertInsertsAutoIncrementKey() {
            return false;
        }
    },

    POSTGRESQL {
        @Override
        public String quoteIdentifier(String identifier) {
            return "\"" + identifier + "\"";
        }

        @Override
        public String limit(int size, int offset) {
            if (offset == 0) {
                return " LIMIT " + size;
            }
            return " LIMIT " + size + " OFFSET " + offset;
        }

        /**
         * {@code ON CONFLICT (<PK 컬럼들>) DO UPDATE SET ...}.
         * 갱신할 컬럼이 하나도 없으면 {@code DO NOTHING} (빈 SET 은 문법 오류).
         */
        @Override
        public String upsertClause(List<String> conflictColumns, String updateSet) {
            String target = " ON CONFLICT (" + String.join(", ", conflictColumns) + ")";
            if (updateSet == null || updateSet.isBlank()) {
                return target + " DO NOTHING";
            }
            return target + " DO UPDATE SET " + updateSet;
        }

        @Override
        public String insertIgnoreInto() {
            return "INSERT INTO ";
        }

        /**
         * 충돌(unique/PK 위반)만 무시한다. MySQL {@code INSERT IGNORE} 와 달리 값 잘림 등
         * 다른 오류는 그대로 던진다 — 의도된 차이다.
         */
        @Override
        public String insertIgnoreSuffix() {
            return " ON CONFLICT DO NOTHING";
        }

        @Override
        public boolean usesJdbcGeneratedKeys() {
            return true;
        }

        /**
         * {@code ON CONFLICT (pk)} 는 INSERT 에 PK 값이 있어야 충돌한다. 자동증가 PK 를 빼면
         * 시퀀스로 새 PK 가 생겨 갱신 대신 새 행이 된다(다른 unique 가 있으면 그쪽 위반 오류).
         */
        @Override
        public boolean upsertInsertsAutoIncrementKey() {
            return true;
        }
    };

    /** 테이블/스키마 식별자를 방언의 따옴표로 감싼다. */
    public abstract String quoteIdentifier(String identifier);

    /** {@code XPaginationSql.limit()} 이 붙이는 LIMIT 절 (앞 공백 포함). size/offset 은 호출자가 이미 보정한 값. */
    public abstract String limit(int size, int offset);

    /** upsert 의 INSERT 뒤에 붙는 충돌 처리 절 (앞 공백 포함). */
    public abstract String upsertClause(List<String> conflictColumns, String updateSet);

    /** 일괄 insert(중복 무시)의 시작 키워드 — 뒤 공백 포함. */
    public abstract String insertIgnoreInto();

    /** 일괄 insert(중복 무시)의 VALUES 뒤에 붙는 절 (앞 공백 포함, 없으면 빈 문자열). */
    public abstract String insertIgnoreSuffix();

    /**
     * {@code insert()} 후 생성 키를 JDBC {@code getGeneratedKeys()} 로 회수하는지.
     * false 면 {@code SELECT LAST_INSERT_ID()} (MySQL, 기존 경로).
     */
    public abstract boolean usesJdbcGeneratedKeys();

    /**
     * upsert 의 INSERT 컬럼에 자동증가 PK 를 넣는지 ({@code save()} 는 PK 가 다 채워졌을 때만 upsert 한다).
     * MySQL 은 1.4.2 출력 유지를 위해 넣지 않는다 — {@code ON DUPLICATE KEY} 는 아무 unique 키에나 걸린다.
     */
    public abstract boolean upsertInsertsAutoIncrementKey();

    /** {@code schema.table} 또는 {@code table} 을 방언 따옴표로 감싸 돌려준다. */
    public String qualifiedTableName(String schema, String table) {
        if (schema != null && !schema.isEmpty()) {
            return quoteIdentifier(schema) + "." + quoteIdentifier(table);
        }
        return quoteIdentifier(table);
    }

    /**
     * 설정값({@code axim.mybatis.dialect})을 해석한다.
     * {@code mysql}·{@code mariadb} → MYSQL, {@code postgresql}·{@code postgres}·{@code pg} → POSTGRESQL.
     *
     * @throws IllegalArgumentException 알 수 없는 값 (오타를 조용히 MySQL 로 넘기지 않는다)
     */
    public static XDialect fromName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("dialect name must not be null");
        }
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "mysql":
            case "mariadb":
                return MYSQL;
            case "postgresql":
            case "postgres":
            case "pg":
                return POSTGRESQL;
            default:
                throw new IllegalArgumentException(
                        "Unknown axim.mybatis.dialect '" + name + "'. Supported: mysql, postgresql");
        }
    }

    /**
     * {@code DatabaseMetaData.getDatabaseProductName()} 값으로 방언을 고른다.
     *
     * @return 알 수 없는 제품이면 {@code null}
     */
    public static XDialect fromProductName(String productName) {
        if (productName == null) {
            return null;
        }
        String p = productName.toLowerCase(Locale.ROOT);
        if (p.contains("postgresql")) {
            return POSTGRESQL;
        }
        if (p.contains("mysql") || p.contains("mariadb")) {
            return MYSQL;
        }
        return null;
    }

    /** 커넥션 메타데이터로 감지. 실패하거나 모르는 제품이면 {@code null}. */
    public static XDialect detect(Connection connection) {
        try {
            return fromProductName(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException | RuntimeException e) {
            return null;
        }
    }

    /** DataSource 에서 커넥션을 하나 빌려 감지. 실패하면 {@code null}. */
    public static XDialect detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return detect(connection);
        } catch (SQLException | RuntimeException e) {
            return null;
        }
    }
}
