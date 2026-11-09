package one.axim.framework.rest.proxy;

import org.springframework.web.client.RestClient;

/**
 * baseUrl 별 {@link XWebClient} 를 만든다.
 *
 * <p>호출마다 <b>새 인스턴스</b>를 돌려준다 (v1.5.0). 1.4.x 는 baseUrl 별로 인스턴스를 캐시해 공유했는데,
 * {@link XWebClient#errorHandler(one.axim.framework.rest.handler.XErrorResponseHandler)} 가 그 공유 인스턴스를
 * 변경하므로 {@code factory.create(url).errorHandler(...)} 가 같은 baseUrl 을 쓰는 다른 모든 사용처의
 * 에러 핸들러까지 바꿨다. 커넥션 풀은 주입된 {@link RestClient}(httpclient5) 가 갖고 있으므로
 * 인스턴스를 새로 만들어도 연결 재사용에는 영향이 없다. 만든 클라이언트는 필드에 보관해 재사용하면 된다.
 */
public class XWebClientFactory {

    private final RestClient restClient;
    private final boolean isDebug;

    public XWebClientFactory(RestClient restClient, boolean isDebug) {
        this.restClient = restClient;
        this.isDebug = isDebug;
    }

    public XWebClient create(String baseUrl) {
        XWebClient client = new XWebClient(restClient, baseUrl);
        client.setDebug(isDebug);
        return client;
    }
}
