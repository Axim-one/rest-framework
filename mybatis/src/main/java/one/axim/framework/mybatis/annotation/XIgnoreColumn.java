package one.axim.framework.mybatis.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by dudgh on 2017. 6. 5..
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface XIgnoreColumn {

}
