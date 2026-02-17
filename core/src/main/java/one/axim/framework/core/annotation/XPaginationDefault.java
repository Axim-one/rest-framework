package one.axim.framework.core.annotation;

import one.axim.framework.core.data.XDirection;

import java.lang.annotation.*;

/**
 * Created by dudgh on 2017. 6. 13..
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface XPaginationDefault {

    /**
     * Offset 설정
     *
     * @return Offset
     */
    int offset() default 0;

    /**
     * Size 설정
     *
     * @return Size
     */
    int size() default 10;

    /**
     * Page 설정
     *
     * @return Page
     */
    int page() default 1;

    /**
     * Sort 대상 컬럼
     *
     * @return
     */
    String column() default "";

    /**
     * 정렬 방법 (ASC|DESC) 설정
     *
     * @return Order By 정렬 방법
     */
    XDirection direction() default XDirection.DESC;
}