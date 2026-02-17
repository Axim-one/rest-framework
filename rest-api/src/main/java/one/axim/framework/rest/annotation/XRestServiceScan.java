package one.axim.framework.rest.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface XRestServiceScan {
    String[] value();
}