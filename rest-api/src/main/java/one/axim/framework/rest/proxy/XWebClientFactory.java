package one.axim.framework.rest.proxy;

import org.springframework.web.client.RestClient;

import java.util.concurrent.ConcurrentHashMap;

public class XWebClientFactory {

    private final RestClient restClient;
    private final boolean isDebug;
    private final ConcurrentHashMap<String, XWebClient> clientCache = new ConcurrentHashMap<>();

    public XWebClientFactory(RestClient restClient, boolean isDebug) {
        this.restClient = restClient;
        this.isDebug = isDebug;
    }

    public XWebClient create(String baseUrl) {
        return clientCache.computeIfAbsent(baseUrl, url -> {
            XWebClient client = new XWebClient(restClient, url);
            client.setDebug(isDebug);
            return client;
        });
    }
}
