package one.axim.framework.rest.configuration;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.TimeUnit;

import one.axim.framework.rest.proxy.XRestClientProxy;
import one.axim.framework.rest.proxy.XRestClientProxyFactoryBean;
import one.axim.framework.rest.proxy.XWebClientFactory;

/**
 * Created by dudgh on 2017. 6. 16..
 */
@Configuration
public class XRestConfiguration {

    @Value("${x.rest.client.pool-size:200}")
    private int maxThreadPoolCount;

    @Value("${x.rest.client.connection-request-timeout:30}")
    private int connectionRequestTimeoutSeconds;

    @Value("${x.rest.client.response-timeout:30}")
    private int responseTimeoutSeconds;

    @Bean
    public RestTemplate makeRestTemplate(CloseableHttpClient client) {

        return newRestTemplateInstance(client);
    }

    @Bean
    public RestClient restClient(CloseableHttpClient httpClient) {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    @Bean
    public static XRestClientBeanDefinitionRegistryPostProcessor xRestClientBeanDefinitionRegistryPostProcessor() {
        return new XRestClientBeanDefinitionRegistryPostProcessor();
    }

    @Bean
    public static XRestClientProxy xRestClientProxy() {
        return new XRestClientProxy();
    }

    @Bean(name = "xRestClientProxyFactoryBean")
    public static XRestClientProxyFactoryBean xRestClientProxyFactoryBean() {
        return new XRestClientProxyFactoryBean();
    }

    @Bean
    public static XWebClientBeanDefinitionRegistryPostProcessor xWebClientBeanDefinitionRegistryPostProcessor() {
        return new XWebClientBeanDefinitionRegistryPostProcessor();
    }

    @Bean(name = "xWebClientFactory")
    public XWebClientFactory xWebClientFactory(RestClient restClient,
                                                @Value("${x.rest.debug:false}") boolean isDebug) {
        return new XWebClientFactory(restClient, isDebug);
    }

    public RestTemplate newRestTemplateInstance(HttpClient client) {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
    }

    @Bean
    public RequestConfig requestConfig() {
        RequestConfig result = RequestConfig.custom()
                                            .setConnectionRequestTimeout(connectionRequestTimeoutSeconds, TimeUnit.SECONDS)
                                            .setResponseTimeout(responseTimeoutSeconds, TimeUnit.SECONDS)
                                            .build();
        return result;
    }

    @Bean
    public CloseableHttpClient httpClient(PoolingHttpClientConnectionManager poolingHttpClientConnectionManager,
            RequestConfig requestConfig) {

        CloseableHttpClient result = HttpClientBuilder.create()
                                                      .setConnectionManager(poolingHttpClientConnectionManager)
                                                      .setDefaultRequestConfig(requestConfig)
                                                      .build();

        return result;
    }

    @Bean
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager() {
        PoolingHttpClientConnectionManager result = new PoolingHttpClientConnectionManager();

        result.setMaxTotal(maxThreadPoolCount);
        result.setDefaultMaxPerRoute(maxThreadPoolCount);

        return result;
    }


}