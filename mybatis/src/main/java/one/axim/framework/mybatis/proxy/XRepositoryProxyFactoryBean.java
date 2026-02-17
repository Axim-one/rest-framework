package one.axim.framework.mybatis.proxy;

import one.axim.framework.mybatis.mapper.CommonMapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Proxy;

public class XRepositoryProxyFactoryBean implements FactoryBean<Object>, ApplicationContextAware {

    private Class<?> repositoryInterface;
    private Class<?> keyClass;
    private Class<?> modelClass;
    private ApplicationContext applicationContext;

    public XRepositoryProxyFactoryBean(Class<?> repositoryInterface, Class<?> keyClass, Class<?> modelClass) {
        this.repositoryInterface = repositoryInterface;
        this.keyClass = keyClass;
        this.modelClass = modelClass;
    }

    @Override
    public Object getObject() throws Exception {
        CommonMapper commonMapper = applicationContext.getBean(CommonMapper.class);
        return Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class<?>[]{repositoryInterface},
                new XRepositoryProxy(commonMapper, repositoryInterface, keyClass, modelClass)
        );
    }

    @Override
    public Class<?> getObjectType() {
        return repositoryInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
