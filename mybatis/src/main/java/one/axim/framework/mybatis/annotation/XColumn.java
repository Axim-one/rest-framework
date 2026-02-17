package one.axim.framework.mybatis.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by dudgh on 2017. 5. 27..
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface XColumn {

    /**
     * 컬럼명 설정
     *
     * @return 컬럼명
     */
    String value() default "";

    /**
     * PK 여부 설정
     *
     * @return PK 여부
     */
    boolean isPrimaryKey() default false;

    /**
     * 업데이트 허용 여부 설정
     *
     * @return 업데이트 허용 여부
     */
    boolean update() default true;

    /**
     * 삽입 허용 여부 설정
     *
     * @return 삽입 허용 여부
     */
    boolean insert() default true;

    /**
     * Auto Increment 여부 설정
     *
     * @return Auto Increment 여부
     */
    boolean isAutoIncrement() default false;
}
