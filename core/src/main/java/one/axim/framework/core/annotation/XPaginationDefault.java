package one.axim.framework.core.annotation;

import one.axim.framework.core.data.XDirection;
import one.axim.framework.core.data.XPagination;

import java.lang.annotation.*;

/**
 * Declares default pagination values for an {@link one.axim.framework.core.data.XPagination} parameter.
 *
 * <p>Page numbers are <strong>1-indexed</strong>: page 1 is the first page.</p>
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
     * 페이지 크기 (기본값: {@value one.axim.framework.core.data.XPagination#DEFAULT_SIZE})
     *
     * @return Size
     */
    int size() default XPagination.DEFAULT_SIZE;

    /**
     * 페이지 번호, 1부터 시작 (기본값: {@value one.axim.framework.core.data.XPagination#DEFAULT_PAGE})
     *
     * @return Page
     */
    int page() default XPagination.DEFAULT_PAGE;

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