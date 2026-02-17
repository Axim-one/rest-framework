package one.axim.framework.mybatis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by dudgh on 2017. 5. 26..
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface XEntity {

    /**
     * 테이블 이름 설정
     *
     * @return 테이블 이름
     */
    String value() default "";

    /**
     * DB 스키마 명 설정 (Optional)
     *
     * @return DB 스키마 명
     */
    String schema() default "";
}
