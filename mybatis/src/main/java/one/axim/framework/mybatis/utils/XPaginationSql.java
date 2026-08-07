package one.axim.framework.mybatis.utils;

import one.axim.framework.core.data.XPagination;
import one.axim.framework.core.utils.NamingConvert;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class XPaginationSql {

    /**
     * ORDER BY 컬럼 화이트리스트.
     *
     * <p>{@code table_alias.column} 형태의 한정자(qualifier)를 허용한다 — JOIN 쿼리에서
     * 같은 이름의 컬럼이 여러 테이블에 있으면 접두사 없이는 ambiguous 오류가 나기 때문이다.
     * 공백/따옴표/괄호/세미콜론은 여전히 불허하므로 주입 위험은 없다. (v1.4.1)
     */
    private static final Pattern SAFE_COLUMN =
            Pattern.compile("^([a-zA-Z_][a-zA-Z0-9_]{0,63}\\.)?[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    private XPaginationSql() {
    }

    public static String orderBy(XPagination pagination) {
        if (pagination == null || !pagination.hasOrder()) {
            return "";
        }
        return " ORDER BY " + pagination.getOrders().stream()
                .map(order -> {
                    String col = NamingConvert.toUnderScoreName(order.getColumn());
                    if (!SAFE_COLUMN.matcher(col).matches()) {
                        throw new IllegalArgumentException("Unsafe ORDER BY column: " + col);
                    }
                    return col + " " + order.getDirection();
                })
                .collect(Collectors.joining(", "));
    }

    public static String limit(XPagination pagination) {
        if (pagination == null || !pagination.hasLimit()) {
            return "";
        }
        int size = Math.max(1, Math.min(pagination.getSize(), 10000));
        int offset = Math.max(0, pagination.getOffset());
        if (offset == 0) {
            return " LIMIT " + size;
        }
        return " LIMIT " + offset + ", " + size;
    }

    /**
     * 쿼리 최상위(괄호 깊이 0)의 마지막 {@code ORDER BY} 이후를 잘라낸다. (v1.4.1)
     *
     * <p>페이지네이션이 명시 정렬을 가질 때, 호출자 SQL 에 이미 있는 ORDER BY 를 제거하고
     * 그 자리를 명시 정렬로 대체하기 위해 쓴다. 제거하지 않으면
     * {@code ... ORDER BY a DESC ORDER BY b DESC LIMIT 20} 이 되어 SQL 문법 오류가 난다.
     *
     * <p>문자열 리터럴({@code '...'}, {@code "..."}, {@code `...`}) 안과 괄호 안(서브쿼리,
     * 윈도우 함수 {@code OVER (ORDER BY ...)})의 ORDER BY 는 건드리지 않는다.
     *
     * <p><b>주의</b>: 최상위 ORDER BY 뒤에 오는 모든 것(예: 호출자가 직접 쓴 LIMIT)이 함께
     * 잘린다. 페이지네이션 쿼리에 LIMIT 을 직접 쓰는 것은 원래 금지 사항이다.
     */
    public static String stripTrailingOrderBy(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        int depth = 0;
        int cut = -1;
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && (c == 'o' || c == 'O') && isOrderByAt(sql, i)) {
                cut = i;
            }
        }
        return cut >= 0 ? sql.substring(0, cut).trim() : sql;
    }

    /** {@code i} 위치에서 단어 경계를 지킨 {@code ORDER <공백> BY} 가 시작하는지 검사한다. */
    private static boolean isOrderByAt(String sql, int i) {
        if (i > 0 && isWordChar(sql.charAt(i - 1))) {
            return false;
        }
        int p = i;
        if (!regionMatchesIgnoreCase(sql, p, "ORDER")) {
            return false;
        }
        p += 5;
        int wsStart = p;
        while (p < sql.length() && Character.isWhitespace(sql.charAt(p))) {
            p++;
        }
        if (p == wsStart) {
            return false;
        }
        if (!regionMatchesIgnoreCase(sql, p, "BY")) {
            return false;
        }
        p += 2;
        return p >= sql.length() || !isWordChar(sql.charAt(p));
    }

    private static boolean regionMatchesIgnoreCase(String sql, int offset, String word) {
        return sql.regionMatches(true, offset, word, 0, word.length());
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
