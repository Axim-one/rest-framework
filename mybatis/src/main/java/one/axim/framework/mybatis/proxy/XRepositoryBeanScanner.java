package one.axim.framework.mybatis.proxy;

import one.axim.framework.mybatis.annotation.XRepository;
import one.axim.framework.mybatis.repository.IXRepository;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

public class XRepositoryBeanScanner extends ClassPathBeanDefinitionScanner {

    private final BeanDefinitionRegistry registry;

    public XRepositoryBeanScanner(BeanDefinitionRegistry registry) {
        super(registry, false);
        this.registry = registry;
        addIncludeFilter(new AnnotationTypeFilter(XRepository.class));
    }

    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);
        for (BeanDefinitionHolder holder : beanDefinitions) {
            processBeanDefinition((GenericBeanDefinition) holder.getBeanDefinition());
        }
        return beanDefinitions;
    }

    private void processBeanDefinition(GenericBeanDefinition beanDefinition) {
        try {
            Class<?> repositoryInterface = Class.forName(beanDefinition.getBeanClassName());
            ParameterizedType pType = findIXRepositoryType(repositoryInterface);

            if (pType == null) {
                throw new IllegalStateException(
                        repositoryInterface.getName() + " does not extend IXRepository with type parameters");
            }

            Class<?> keyClass = (Class<?>) pType.getActualTypeArguments()[0];
            Class<?> modelClass = (Class<?>) pType.getActualTypeArguments()[1];

            beanDefinition.setBeanClass(XRepositoryProxyFactoryBean.class);
            beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(repositoryInterface);
            beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(keyClass);
            beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(modelClass);
            beanDefinition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_TYPE);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private ParameterizedType findIXRepositoryType(Class<?> clazz) {
        for (Type type : clazz.getGenericInterfaces()) {
            if (type instanceof ParameterizedType pt) {
                if (IXRepository.class.isAssignableFrom((Class<?>) pt.getRawType())) {
                    return pt;
                }
            }
        }
        // Search parent interfaces recursively
        for (Class<?> iface : clazz.getInterfaces()) {
            ParameterizedType result = findIXRepositoryType(iface);
            if (result != null) return result;
        }
        return null;
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        return beanDefinition.getMetadata().isInterface();
    }
}
