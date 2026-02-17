package one.axim.framework.rest.configuration;

import one.axim.framework.rest.annotation.XRestService;
import one.axim.framework.rest.annotation.XRestServiceScan;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class XRestClientBeanScanner extends ClassPathBeanDefinitionScanner {
    public XRestClientBeanScanner(BeanDefinitionRegistry registry) {
        super(registry);

        initialize();
    }

    private void initialize() {
        resetFilters(false);
        addIncludeFilter(new AnnotationTypeFilter(XRestService.class));
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
    }

    public void scanAfterUpdateBeanDefinition() throws ClassNotFoundException {

        List<String> basePackages = new ArrayList<>();
        BeanDefinitionRegistry registry = getRegistry();

        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition bd = registry.getBeanDefinition(beanName);
            String className = bd.getBeanClassName();
            if (className == null) continue;

            try {
                Class<?> beanClass = Class.forName(className);
                XRestServiceScan serviceScan = beanClass.getAnnotation(XRestServiceScan.class);
                if (serviceScan != null) {
                    for (String pkg : serviceScan.value()) {
                        if (StringUtils.hasText(pkg)) {
                            basePackages.add(pkg);
                        }
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // skip beans whose class isn't loadable at this stage
            }
        }

        if (!basePackages.isEmpty()) {
            Set<BeanDefinitionHolder> definitionHolders = super.doScan(basePackages.toArray(new String[0]));

            for (BeanDefinitionHolder definitionHolder : definitionHolders) {
                GenericBeanDefinition definition = (GenericBeanDefinition) (definitionHolder.getBeanDefinition());

                Class<?> clazz = Class.forName(definition.getBeanClassName());

                ConstructorArgumentValues args = new ConstructorArgumentValues();

                args.addGenericArgumentValue(clazz.getClassLoader());
                args.addGenericArgumentValue(clazz);
                definition.setConstructorArgumentValues(args);

                definition.setBeanClass(clazz);
                definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
                definition.setFactoryBeanName("xRestClientProxyFactoryBean");
                definition.setFactoryMethodName("createXRestClientProxyBean");
                definition.setLazyInit(true);
            }
        }
    }
}
