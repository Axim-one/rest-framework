package one.axim.framework.mybatis.utils;

import one.axim.framework.core.data.XPagination;
import one.axim.framework.core.utils.NamingConvert;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class XPaginationSql {

    private static final Pattern SAFE_COLUMN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

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
}