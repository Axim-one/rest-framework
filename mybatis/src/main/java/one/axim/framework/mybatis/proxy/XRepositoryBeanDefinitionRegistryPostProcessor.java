package one.axim.framework.mybatis.proxy;

import one.axim.framework.mybatis.annotation.XRepositoryScan;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

public class XRepositoryBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        String[] basePackages = getBasePackages();
        if (basePackages.length > 0) {
            XRepositoryBeanScanner scanner = new XRepositoryBeanScanner(registry);
            scanner.scan(basePackages);
        }
    }

    private String[] getBasePackages() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(XRepositoryScan.class);
        for (Object bean : beans.values()) {
            AnnotationMetadata metadata = AnnotationMetadata.introspect(bean.getClass());
            Map<String, Object> attributes = metadata.getAnnotationAttributes(XRepositoryScan.class.getName());
            if (attributes != null) {
                return (String[]) attributes.get("value");
            }
        }
        return new String[0];
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
