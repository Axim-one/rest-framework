package one.axim.framework.rest.resolver;

import one.axim.framework.rest.handler.XAccessTokenParseHandler;
import one.axim.framework.rest.model.SessionData;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

public class XSessionResolver implements HandlerMethodArgumentResolver {

    private final XAccessTokenParseHandler xAccessTokenParseHandler;

    public XSessionResolver(XAccessTokenParseHandler xAccessTokenParseHandler) {
        this.xAccessTokenParseHandler = xAccessTokenParseHandler;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return SessionData.class.isAssignableFrom(parameter.getParameterType());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        if (this.xAccessTokenParseHandler != null && request != null) {
            Class<? extends SessionData> sessionClass = (Class<? extends SessionData>) parameter.getParameterType();
            return this.xAccessTokenParseHandler.validateSession(request, sessionClass);
        }

        return null;
    }
}
