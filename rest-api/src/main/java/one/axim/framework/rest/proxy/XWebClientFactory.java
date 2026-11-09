package one.axim.framework.rest.proxy;

import org.springframework.web.client.RestClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * baseUrl 별 {@link XWebClient} 를 만든다.
 *
 * <p>호출마다 <b>새 {@link XWebClient} 인스턴스</b>를 돌려준다 (v1.5.0). 1.4.x 는 baseUrl 별로 인스턴스를 캐시해
 * 공유했는데, {@link XWebClient#errorHandler(one.axim.framework.rest.handler.XErrorResponseHandler)} 가 그 공유
 * 인스턴스를 변경하므로 {@code factory.create(url).errorHandler(...)} 가 같은 baseUrl 을 쓰는 다른 모든 사용처의
 * 에러 핸들러까지 바꿨다.
 *
 * <p>baseUrl 이 적용된 {@link RestClient} 는 계속 캐시하므로 호출마다 {@code create()} 해도 비용은 1.4.x 와 같다.
 * 커넥션 풀은 주입된 RestClient(httpclient5) 가 갖는다.
 */
public class XWebClientFactory {

    private final RestClient restClient;
    private final boolean isDebug;
    private final ConcurrentHashMap<String, RestClient> boundClients = new ConcurrentHashMap<>();

    public XWebClientFactory(RestClient restClient, boolean isDebug) {
        this.restClient = restClient;
        this.isDebug = isDebug;
    }

    public XWebClient create(String baseUrl) {
        RestClient bound = boundClients.computeIfAbsent(baseUrl, url -> restClient.mutate().baseUrl(url).build());
        XWebClient client = new XWebClient(bound, baseUrl, true);
        client.setDebug(isDebug);
        return client;
    }
}
