package one.axim.framework.rest.configuration;

import one.axim.framework.rest.handler.XAccessTokenParseHandler;
import one.axim.framework.rest.resolver.XPaginationResolver;
import one.axim.framework.rest.resolver.XSessionResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class XWebMvcConfiguration implements WebMvcConfigurer {

    @Autowired(required = false)
    private XAccessTokenParseHandler xAccessTokenParseHandler;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(new XPaginationResolver());
        argumentResolvers.add(new XSessionResolver(xAccessTokenParseHandler));
    }
}