package one.axim.framework.rest.controller;

import one.axim.framework.rest.handler.XAccessTokenParseHandler;
import one.axim.framework.rest.model.SessionData;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.ParameterizedType;

/**
 * Created by dudgh on 2017. 6. 16..
 */
public abstract class XSessionController<T extends SessionData> extends XAbstractController {

    private Class<T> sessionDataCls;

    @Autowired
    protected XAccessTokenParseHandler xAccessTokenParseHandler;

    @PostConstruct
    @SuppressWarnings("unchecked")
    private void resolveSessionDataClass() {
        Class<?> cls = this.getClass();
        while (cls != null && !cls.equals(Object.class)) {
            if (cls.getGenericSuperclass() instanceof ParameterizedType p) {
                this.sessionDataCls = (Class<T>) p.getActualTypeArguments()[0];
                return;
            }
            cls = cls.getSuperclass();
        }
        throw new IllegalStateException("Could not resolve SessionData type parameter for " + getClass().getName());
    }

    protected T getSession() {
        return xAccessTokenParseHandler.validateSession(httpServletRequest, sessionDataCls);
    }

    protected String generateSessionToken(T obj) {
        return xAccessTokenParseHandler.generateAccessToken(obj);
    }

    protected boolean hasSession() {
        return httpServletRequest.getHeader(XAccessTokenParseHandler.ACCESS_TOKEN_HEADER) != null;
    }
}