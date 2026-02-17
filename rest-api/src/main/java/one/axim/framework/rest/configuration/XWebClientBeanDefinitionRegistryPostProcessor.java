package one.axim.framework.rest.configuration;

import one.axim.framework.rest.proxy.XWebClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;

import java.util.Map;

public class XWebClientBeanDefinitionRegistryPostProcessor
        implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, PriorityOrdered {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        Map<String, String> services = Binder.get(environment)
                .bind("x.web-client.services", Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());

        services.forEach((name, baseUrl) -> {
            GenericBeanDefinition def = new GenericBeanDefinition();
            def.setBeanClass(XWebClient.class);
            def.setFactoryBeanName("xWebClientFactory");
            def.setFactoryMethodName("create");

            ConstructorArgumentValues args = new ConstructorArgumentValues();
            args.addGenericArgumentValue(baseUrl);
            def.setConstructorArgumentValues(args);
            def.setLazyInit(true);

            registry.registerBeanDefinition(name, def);
        });
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
