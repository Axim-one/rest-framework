package one.axim.framework.mybatis.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link XPaginationSql#stripTrailingOrderBy(String)} 검증 (v1.4.1).
 *
 * <p>배경: 호출자 @Select 에 ORDER BY 가 있으면 인터셉터가 자기 ORDER BY 를 덧붙여
 * "... ORDER BY a DESC ORDER BY b DESC LIMIT 20" 이 되어 런타임 SQL 문법 오류가 났다.
 */
class XPaginationSqlTest {

    @Test
    @DisplayName("최상위 ORDER BY 를 잘라낸다")
    void stripsTopLevelOrderBy() {
        assertEquals("SELECT * FROM t WHERE a = 1",
                XPaginationSql.stripTrailingOrderBy("SELECT * FROM t WHERE a = 1 ORDER BY created_at DESC"));
    }

    @Test
    @DisplayName("ORDER BY 가 없으면 원본을 그대로 돌려준다")
    void keepsSqlWithoutOrderBy() {
        String sql = "SELECT * FROM t WHERE a = 1";
        assertSame(sql, XPaginationSql.stripTrailingOrderBy(sql));
    }

    @Test
    @DisplayName("서브쿼리 안의 ORDER BY 는 건드리지 않는다")
    void ignoresOrderByInsideSubquery() {
        String sql = "SELECT * FROM (SELECT x FROM t ORDER BY x DESC) s WHERE s.x = 1";
        assertSame(sql, XPaginationSql.stripTrailingOrderBy(sql));
    }

    @Test
    @DisplayName("윈도우 함수 OVER (ORDER BY ...) 는 건드리지 않는다")
    void ignoresOrderByInsideWindowFunction() {
        String sql = "SELECT ROW_NUMBER() OVER (ORDER BY id) rn, x FROM t";
        assertSame(sql, XPaginationSql.stripTrailingOrderBy(sql));
    }

    @Test
    @DisplayName("서브쿼리와 최상위 ORDER BY 가 함께 있으면 최상위만 잘라낸다")
    void stripsOnlyTopLevelWhenBothPresent() {
        assertEquals("SELECT * FROM (SELECT x FROM t ORDER BY x) s",
                XPaginationSql.stripTrailingOrderBy(
                        "SELECT * FROM (SELECT x FROM t ORDER BY x) s ORDER BY s.x DESC"));
    }

    @Test
    @DisplayName("문자열 리터럴 안의 ORDER BY 는 건드리지 않는다")
    void ignoresOrderByInsideStringLiteral() {
        String sql = "SELECT * FROM t WHERE memo = 'ORDER BY hack'";
        assertSame(sql, XPaginationSql.stripTrailingOrderBy(sql));
    }

    @Test
    @DisplayName("대소문자와 개행/다중 공백을 허용한다")
    void handlesCaseAndWhitespace() {
        assertEquals("SELECT * FROM t",
                XPaginationSql.stripTrailingOrderBy("SELECT * FROM t\n  order   by\n  id  desc"));
    }

    @Test
    @DisplayName("컬럼명에 포함된 order/by 문자열을 ORDER BY 로 오인하지 않는다")
    void doesNotMatchWordFragments() {
        String sql = "SELECT order_by_column, reorder FROM t WHERE order_by_column = 1";
        assertSame(sql, XPaginationSql.stripTrailingOrderBy(sql));
    }

    @Test
    @DisplayName("UNION 뒤의 최상위 ORDER BY 도 잘라낸다")
    void stripsOrderByAfterUnion() {
        assertEquals("SELECT a FROM t1 UNION ALL SELECT a FROM t2",
                XPaginationSql.stripTrailingOrderBy(
                        "SELECT a FROM t1 UNION ALL SELECT a FROM t2 ORDER BY a DESC"));
    }
}
