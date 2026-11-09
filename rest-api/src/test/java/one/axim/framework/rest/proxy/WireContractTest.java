package one.axim.framework.rest.proxy;

import one.axim.framework.rest.annotation.XHttpMethod;
import one.axim.framework.rest.annotation.XRestAPI;
import one.axim.framework.rest.annotation.XRestService;
import one.axim.framework.rest.exception.UnavailableServerException;
import one.axim.framework.rest.exception.XRestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@code @XRestService} / {@code XWebClient} 의 실제 wire 계약 고정 (v1.5.0).
 * 문서(references/rest-client-and-auth.md)의 설명은 이 테스트가 근거다.
 * ADR-0001 §10.1 의 특성화 테스트 중 문서에 적힌 항목만 우선 고정한다.
 */
class WireContractTest {

    public static class Dated {
        public LocalDateTime modern;
        public Date legacy;
    }

    @XRestService(value = "orders", host = "http://orders.test")
    interface OrdersApi {
        @XRestAPI(value = "/orders", method = XHttpMethod.GET)
        Map<String, Object> search(@RequestParam("status") String status);

        @XRestAPI(value = "/auth/token", method = XHttpMethod.POST)
        Map<String, Object> token(@RequestParam("grant_type") String grantType, @RequestParam("code") String code);

        @XRestAPI(value = "/orders", method = XHttpMethod.POST)
        Map<String, Object> createWithMap(@RequestBody Map<String, Object> body);

        @XRestAPI(value = "/orders", method = XHttpMethod.POST)
        Map<String, Object> createWithIgnoredParam(@RequestParam("dryRun") String dryRun, @RequestBody Map<String, Object> body);

        @XRestAPI(value = "/orders?dryRun={dryRun}", method = XHttpMethod.POST)
        Map<String, Object> createWithQueryInPath(@PathVariable("dryRun") String dryRun, @RequestBody Map<String, Object> body);

        @XRestAPI(value = "/dated", method = XHttpMethod.POST)
        Map<String, Object> postDated(@RequestBody Dated body);
    }

    @XRestService(value = "billing")
    interface GatewayOnlyApi {
        @XRestAPI(value = "/invoices", method = XHttpMethod.GET)
        Map<String, Object> list();
    }

    RestTemplate restTemplate;
    MockRestServiceServer server;
    XRestClientProxy handler;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        handler = new XRestClientProxy();
        ReflectionTestUtils.setField(handler, "restTemplate", restTemplate);
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.refresh();
        ReflectionTestUtils.setField(handler, "applicationContext", ctx);
        ReflectionTestUtils.setField(handler, "gatewayHost", "");
    }

    @SuppressWarnings("unchecked")
    <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    // ── @RequestParam 위치 ──

    @Test
    @DisplayName("GET: @RequestParam → query string")
    void getParamIsQueryString() {
        server.expect(requestTo("http://orders.test/orders?status=ACTIVE")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        proxy(OrdersApi.class).search("ACTIVE");
        server.verify();
    }

    @Test
    @DisplayName("POST(@RequestBody 없음): @RequestParam → application/x-www-form-urlencoded 바디")
    void postParamIsFormBody() {
        server.expect(requestTo("http://orders.test/auth/token")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8"))
                .andExpect(content().formDataContains(Map.of("grant_type", "authorization_code", "code", "abc")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        proxy(OrdersApi.class).token("authorization_code", "abc");
        server.verify();
    }

    @Test
    @DisplayName("POST @RequestBody Map → form 이 아니라 JSON")
    void postMapBodyIsJson() {
        server.expect(requestTo("http://orders.test/orders"))
                .andExpect(header("Content-Type", "application/json; charset=UTF-8"))
                .andExpect(content().json("{\"a\":1}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        proxy(OrdersApi.class).createWithMap(Map.of("a", 1));
        server.verify();
    }

    @Test
    @DisplayName("POST @RequestBody + @RequestParam → @RequestParam 은 버려진다")
    void postBodyDropsRequestParam() {
        server.expect(requestTo("http://orders.test/orders"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        proxy(OrdersApi.class).createWithIgnoredParam("true", Map.of("a", 1));
        server.verify();
    }

    @Test
    @DisplayName("POST JSON + query: path 템플릿에 넣으면 query string 으로 나간다")
    void queryInPathTemplate() {
        server.expect(requestTo("http://orders.test/orders?dryRun=true"))
                .andExpect(content().json("{\"a\":1}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        proxy(OrdersApi.class).createWithQueryInPath("true", Map.of("a", 1));
        server.verify();
    }

    // ── 날짜 직렬화 ──

    @Test
    @DisplayName("@XRestService 요청 바디: RestTemplate 기본 컨버터 — LocalDateTime 은 배열, Date 는 epoch millis (Boot 서버가 읽을 수 있음)")
    void xRestServiceRequestDateFormat() {
        Dated d = new Dated();
        d.modern = LocalDateTime.of(2024, 1, 15, 14, 30, 0);
        d.legacy = new Date(1705329000000L); // 2024-01-15T14:30:00Z
        server.expect(requestTo("http://orders.test/dated"))
                .andExpect(content().json("{\"modern\":[2024,1,15,14,30],\"legacy\":1705329000000}", true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        proxy(OrdersApi.class).postDated(d);
        server.verify();
    }

    @Test
    @DisplayName("@XRestService 응답: LocalDateTime ISO-8601 은 읽는다")
    void xRestServiceReadsIsoLocalDateTime() throws Exception {
        server.expect(requestTo("http://orders.test/dated"))
                .andRespond(withSuccess("{\"modern\":\"2024-01-15T14:30:00\"}", MediaType.APPLICATION_JSON));
        XRestClient client = new XRestClient("http://orders.test", "orders", restTemplate);
        Dated back = client.post("/dated", Dated.class, (Object) new Dated(), null, Map.of());
        assertEquals(LocalDateTime.of(2024, 1, 15, 14, 30, 0), back.modern);
    }

    @Test
    @DisplayName("@XRestService 응답: Spring Boot 서버의 ISO-8601 java.util.Date 는 읽지 못한다 (ADR-0001 §3.3, 응답 방향만)")
    void xRestServiceCannotReadIsoDate() {
        server.expect(requestTo("http://orders.test/dated"))
                .andRespond(withSuccess("{\"legacy\":\"2024-01-15T14:30:00.000+00:00\"}", MediaType.APPLICATION_JSON));
        XRestClient client = new XRestClient("http://orders.test", "orders", restTemplate);
        Exception e = assertThrows(Exception.class,
                () -> client.post("/dated", Dated.class, (Object) new Dated(), null, Map.of()));
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertTrue(String.valueOf(root.getMessage()).contains("java.util.Date"), String.valueOf(root));
    }

    @Test
    @DisplayName("XWebClient 요청 바디: RestClient 기본 컨버터 — LocalDateTime 은 배열, Date 는 epoch millis")
    void xWebClientDateFormat() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer webServer = MockRestServiceServer.bindTo(builder).build();
        XWebClient client = new XWebClient(builder.build(), "http://pay.test");
        Dated d = new Dated();
        d.modern = LocalDateTime.of(2024, 1, 15, 14, 30, 0);
        d.legacy = new Date(1705329000000L);
        webServer.expect(requestTo("http://pay.test/dated"))
                .andExpect(content().json("{\"modern\":[2024,1,15,14,30],\"legacy\":1705329000000}", true))
                .andRespond(withSuccess("{\"modern\":\"2024-01-15T14:30:00\",\"legacy\":\"2024-01-15T14:30:00.000+00:00\"}",
                        MediaType.APPLICATION_JSON));
        Dated back = client.post("/dated", d, Dated.class);
        webServer.verify();
        assertEquals(LocalDateTime.of(2024, 1, 15, 14, 30, 0), back.modern, "응답의 ISO-8601 은 읽는다");
        assertEquals(1705329000000L, back.legacy.getTime());
    }

    // ── v1.5.0 수정 ──

    @Test
    @DisplayName("gateway 모드인데 axim.rest.gateway.host 가 없으면 원인을 알려주는 예외 (1.4.x: https://@null/...)")
    void missingGatewayHostFailsClearly() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> proxy(GatewayOnlyApi.class).list());
        assertTrue(e.getMessage().contains("axim.rest.gateway.host"), e.getMessage());
        assertTrue(e.getMessage().contains("billing"), e.getMessage());
    }

    @Test
    @DisplayName("XWebClientFactory.create(): 호출마다 새 인스턴스 — errorHandler 가 다른 사용처로 새지 않는다")
    void factoryReturnsIsolatedInstances() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer webServer = MockRestServiceServer.bindTo(builder).build();
        XWebClientFactory factory = new XWebClientFactory(builder.build(), false);

        XWebClient custom = factory.create("http://pay.test")
                .errorHandler((status, body) -> {
                    one.axim.framework.rest.model.ApiError err = new one.axim.framework.rest.model.ApiError();
                    err.setCode("CUSTOM");
                    err.setMessage("custom");
                    return new XRestException(HttpStatus.I_AM_A_TEAPOT, err);
                });
        XWebClient plain = factory.create("http://pay.test");
        assertNotSame(custom, plain);

        webServer.expect(requestTo("http://pay.test/x")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"E1\",\"message\":\"bad\"}"));
        webServer.expect(requestTo("http://pay.test/x")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"E1\",\"message\":\"bad\"}"));

        XRestException a = assertThrows(XRestException.class, () -> custom.get("/x", Map.class));
        XRestException b = assertThrows(XRestException.class, () -> plain.get("/x", Map.class));
        assertEquals("CUSTOM", a.getCode());
        assertEquals("E1", b.getCode(), "다른 인스턴스는 기본 파서를 써야 한다");
    }

    @Test
    @DisplayName("XWebClient 연결 실패 예외에 baseUrl 이 실린다")
    void unavailableCarriesBaseUrl() {
        XWebClient client = XWebClient.create("http://127.0.0.1:1");
        UnavailableServerException e = assertThrows(UnavailableServerException.class, () -> client.get("/x", Map.class));
        assertEquals("http://127.0.0.1:1", e.getDescription());
    }
}
