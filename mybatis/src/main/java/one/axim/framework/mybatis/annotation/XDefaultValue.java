package one.axim.framework.mybatis.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by dudgh on 2017. 5. 29..
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface XDefaultValue {

    /**
     * 컬럼의 기본 값 설정
     *
     * @return 기본값 (DB 기준)
     */
    String value() default "";

    /**
     * DB 기본값 사용 여부
     *
     * @return 사용 여부
     */
    boolean isDBDefaultUsed() default true;

    /**
     * 업데이트시 사용할 값 설정
     *
     * @return 업데이트 값
     */
    String updateValue() default "";


    /**
     * String 의 경우 Default 값이 DB 에서 사용되는 값인지 아니면 String 인지를 판단하기 위한 값
     *
     * @return
     */
    boolean isDBValue() default false;
}
