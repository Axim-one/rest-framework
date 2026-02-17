package one.axim.framework.mybatis.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.Set;

/**
 * Created by dudgh on 2017. 5. 29..
 */
public class ColumnSpec {

    private static final Set<Class<?>> NORMAL_TYPES = Set.of(
            Integer.class, int.class,
            Long.class, long.class,
            Double.class, double.class,
            Float.class, float.class,
            Short.class, short.class,
            Byte.class, boolean.class,
            Boolean.class, byte.class,
            String.class,
            BigDecimal.class, BigInteger.class,
            Date.class, java.sql.Date.class, Timestamp.class,
            LocalDate.class, LocalTime.class, LocalDateTime.class
    );

    public static boolean isNormalType(Class<?> cls) {
        return NORMAL_TYPES.contains(cls);
    }
}