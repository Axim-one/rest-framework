package one.axim.framework.rest.proxy;

import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Proxy;

public class XRestClientProxyFactoryBean {

    @Autowired
    private XRestClientProxy xRestClientProxy;

    @SuppressWarnings("unchecked")
    public <R> R createXRestClientProxyBean(ClassLoader classLoader, Class<R> clazz) {
        return (R) Proxy.newProxyInstance(classLoader, new Class[]{clazz}, xRestClientProxy);
    }

}
