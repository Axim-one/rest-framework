package one.axim.framework.mybatis.annotation;

import org.springframework.context.annotation.Import;
import one.axim.framework.mybatis.proxy.XRepositoryBeanDefinitionRegistryPostProcessor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables scanning for {@link XRepository}-annotated interfaces in the specified base packages.
 *
 * <p>Place this annotation on your Spring Boot application class alongside
 * {@code @MapperScan} and {@code @ComponentScan} to complete the framework setup.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @SpringBootApplication
 * @ComponentScan({"one.axim.framework.rest", "one.axim.framework.mybatis", "com.myapp"})
 * @XRepositoryScan("com.myapp.repository")
 * @MapperScan({"one.axim.framework.mybatis.mapper", "com.myapp"})
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * @see XRepository
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(XRepositoryBeanDefinitionRegistryPostProcessor.class)
public @interface XRepositoryScan {

    /**
     * Base packages to scan for {@link XRepository}-annotated interfaces.
     *
     * @return array of package names
     */
    String[] value() default {};
}
