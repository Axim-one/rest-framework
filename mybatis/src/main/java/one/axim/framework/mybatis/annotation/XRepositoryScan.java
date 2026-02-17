package one.axim.framework.mybatis.annotation;

import org.springframework.context.annotation.Import;
import one.axim.framework.mybatis.proxy.XRepositoryBeanDefinitionRegistryPostProcessor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(XRepositoryBeanDefinitionRegistryPostProcessor.class)
public @interface XRepositoryScan {
    String[] value() default {};
}
