package one.axim.framework.mybatis.dialect;

import java.util.function.Supplier;

/**
 * JVM 전역 방언 보관소 (v1.5.0).
 *
 * <p>{@code CrudSqlProvider}·{@code XResultInterceptor} 는 MyBatis 가 직접 생성하므로
 * Spring 주입을 받을 수 없다. 그래서 {@code XRepositoryConfig} 의 debug 플래그처럼 정적으로 둔다.
 *
 * <p>결정 순서:
 * <ol>
 *   <li>{@code axim.mybatis.dialect} 설정 — {@link XDialectConfig} 가 기동 시 넣는다</li>
 *   <li>설정이 없으면 DataSource 메타데이터로 자동 감지 — 기동 시 {@link XDialectConfig},
 *       그때 실패했으면 첫 쿼리에서 {@code XResultInterceptor} 가 실제 커넥션으로 다시 감지</li>
 *   <li>감지 실패 → MySQL</li>
 * </ol>
 *
 * <p>아직 정해지지 않았을 때 {@link #current()} 는 MySQL 을 돌려준다(결정으로 고정하지는 않는다).
 * 테이블명 따옴표는 메타데이터 파싱 시점이 아니라 SQL 생성 시점에 붙으므로, 리포지토리 빈이
 * 방언 결정보다 먼저 만들어져도 문제없다.
 */
public final class XDialectHolder {

    private static volatile XDialect dialect;

    private XDialectHolder() {
    }

    /** 현재 방언. 미결정이면 MySQL. */
    public static XDialect current() {
        XDialect d = dialect;
        return d != null ? d : XDialect.MYSQL;
    }

    /** 설정 또는 감지로 방언이 정해졌는지. */
    public static boolean isResolved() {
        return dialect != null;
    }

    /** 방언을 명시한다(덮어쓴다). */
    public static void set(XDialect value) {
        dialect = value;
    }

    /** 아직 정해지지 않았을 때만 supplier 결과로 정한다. supplier 가 null 을 주면 MySQL. */
    public static XDialect resolveIfAbsent(Supplier<XDialect> supplier) {
        XDialect d = dialect;
        if (d != null) {
            return d;
        }
        synchronized (XDialectHolder.class) {
            if (dialect == null) {
                XDialect detected = supplier.get();
                dialect = detected != null ? detected : XDialect.MYSQL;
            }
            return dialect;
        }
    }

    /** 미결정 상태로 되돌린다 (테스트용). */
    public static void reset() {
        dialect = null;
    }
}
