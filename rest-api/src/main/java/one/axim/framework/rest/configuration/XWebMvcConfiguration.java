package one.axim.framework.rest.configuration;

import one.axim.framework.rest.handler.XAccessTokenParseHandler;
import one.axim.framework.rest.resolver.XPaginationResolver;
import one.axim.framework.rest.resolver.XSessionResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class XWebMvcConfiguration implements WebMvcConfigurer {

    private static final MediaType APPLICATION_JSON_UTF8 =
            new MediaType("application", "json", StandardCharsets.UTF_8);

    @Autowired(required = false)
    private XAccessTokenParseHandler xAccessTokenParseHandler;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(new XPaginationResolver());
        argumentResolvers.add(new XSessionResolver(xAccessTokenParseHandler));
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter jackson = new MappingJackson2HttpMessageConverter();
        jackson.setDefaultCharset(StandardCharsets.UTF_8);
        jackson.setSupportedMediaTypes(List.of(APPLICATION_JSON_UTF8, MediaType.APPLICATION_JSON));
        converters.add(0, jackson);
    }
}