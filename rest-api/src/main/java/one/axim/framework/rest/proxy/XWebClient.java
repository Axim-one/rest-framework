package one.axim.framework.rest.proxy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import one.axim.framework.rest.exception.UnavailableServerException;
import one.axim.framework.rest.exception.XRestException;
import one.axim.framework.rest.model.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;

public class XWebClient {

    private static final Logger logger = LoggerFactory.getLogger(XWebClient.class);
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private final RestClient restClient;
    private boolean isDebug = false;

    public XWebClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public XWebClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient.mutate()
                .baseUrl(baseUrl)
                .build();
    }

    public static XWebClient create(String baseUrl) {
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        return new XWebClient(restClient);
    }

    // ──────────────────────────────────────────
    // Simple API — Class<T>
    // ──────────────────────────────────────────

    public <T> T get(String uri, Class<T> responseType, Object... uriVariables) {
        return execute(HttpMethod.GET, uri, null, null, responseType, uriVariables);
    }

    public <T> T post(String uri, Object body, Class<T> responseType, Object... uriVariables) {
        return execute(HttpMethod.POST, uri, body, null, responseType, uriVariables);
    }

    public <T> T put(String uri, Object body, Class<T> responseType, Object... uriVariables) {
        return execute(HttpMethod.PUT, uri, body, null, responseType, uriVariables);
    }

    public <T> T patch(String uri, Object body, Class<T> responseType, Object... uriVariables) {
        return execute(HttpMethod.PATCH, uri, body, null, responseType, uriVariables);
    }

    public <T> T delete(String uri, Class<T> responseType, Object... uriVariables) {
        return execute(HttpMethod.DELETE, uri, null, null, responseType, uriVariables);
    }

    // ──────────────────────────────────────────
    // Simple API — ParameterizedTypeReference<T>
    // ──────────────────────────────────────────

    public <T> T get(String uri, ParameterizedTypeReference<T> typeRef, Object... uriVariables) {
        return execute(HttpMethod.GET, uri, null, null, typeRef, uriVariables);
    }

    public <T> T post(String uri, Object body, ParameterizedTypeReference<T> typeRef, Object... uriVariables) {
        return execute(HttpMethod.POST, uri, body, null, typeRef, uriVariables);
    }

    public <T> T put(String uri, Object body, ParameterizedTypeReference<T> typeRef, Object... uriVariables) {
        return execute(HttpMethod.PUT, uri, body, null, typeRef, uriVariables);
    }

    public <T> T patch(String uri, Object body, ParameterizedTypeReference<T> typeRef, Object... uriVariables) {
        return execute(HttpMethod.PATCH, uri, body, null, typeRef, uriVariables);
    }

    public <T> T delete(String uri, ParameterizedTypeReference<T> typeRef, Object... uriVariables) {
        return execute(HttpMethod.DELETE, uri, null, null, typeRef, uriVariables);
    }

    // ──────────────────────────────────────────
    // Builder API
    // ──────────────────────────────────────────

    public RequestSpec spec() {
        return new RequestSpec(this);
    }

    // ──────────────────────────────────────────
    // Core execution — Class<T>
    // ──────────────────────────────────────────

    private <T> T execute(HttpMethod method, String uri, Object body, HttpHeaders extraHeaders,
                          Class<T> responseType, Object... uriVariables) {
        if (this.isDebug) {
            logger.info("{} request uri :: {}", method, uri);
        }

        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(uri, uriVariables)
                    .accept(MediaType.APPLICATION_JSON);

            if (extraHeaders != null && !extraHeaders.isEmpty()) {
                spec.headers(h -> h.addAll(extraHeaders));
            }

            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON);
                spec.body(body);
            }

            T result = spec
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (req, res) -> handleErrorResponse(res.getStatusCode(), res.getBody()))
                    .body(responseType);

            if (this.isDebug) {
                logger.info("response body :: {}", result);
            }

            return result;

        } catch (XRestException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new UnavailableServerException(UnavailableServerException.UNAVAILABLE_SERVICE);
        }
    }

    // ──────────────────────────────────────────
    // Core execution — ParameterizedTypeReference<T>
    // ──────────────────────────────────────────

    private <T> T execute(HttpMethod method, String uri, Object body, HttpHeaders extraHeaders,
                          ParameterizedTypeReference<T> typeRef, Object... uriVariables) {
        if (this.isDebug) {
            logger.info("{} request uri :: {}", method, uri);
        }

        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(uri, uriVariables)
                    .accept(MediaType.APPLICATION_JSON);

            if (extraHeaders != null && !extraHeaders.isEmpty()) {
                spec.headers(h -> h.addAll(extraHeaders));
            }

            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON);
                spec.body(body);
            }

            T result = spec
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (req, res) -> handleErrorResponse(res.getStatusCode(), res.getBody()))
                    .body(typeRef);

            if (this.isDebug) {
                logger.info("response body :: {}", result);
            }

            return result;

        } catch (XRestException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new UnavailableServerException(UnavailableServerException.UNAVAILABLE_SERVICE);
        }
    }

    // ──────────────────────────────────────────
    // Error handling
    // ──────────────────────────────────────────

    private void handleErrorResponse(org.springframework.http.HttpStatusCode statusCode, InputStream body) throws IOException {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());

        if (body == null) {
            throw new XRestException(status, new ApiError(status, status.getReasonPhrase(), null, false));
        }

        byte[] bytes = body.readAllBytes();
        if (bytes.length == 0) {
            throw new XRestException(status, new ApiError(status, status.getReasonPhrase(), null, false));
        }

        try {
            ApiError error = OBJECT_MAPPER.readValue(bytes, ApiError.class);
            throw new XRestException(status, error);
        } catch (XRestException e) {
            throw e;
        } catch (Exception e) {
            String responseBody = new String(bytes);
            throw new XRestException(status, new ApiError(status, responseBody, e, false));
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    public void setDebug(boolean debug) {
        isDebug = debug;
    }

    // ──────────────────────────────────────────
    // RequestSpec (builder)
    // ──────────────────────────────────────────

    public static class RequestSpec {

        private final XWebClient client;
        private HttpMethod method;
        private String uri;
        private Object[] uriVariables;
        private Object body;
        private final HttpHeaders headers = new HttpHeaders();

        private RequestSpec(XWebClient client) {
            this.client = client;
        }

        public RequestSpec get(String uri, Object... uriVariables) {
            this.method = HttpMethod.GET;
            this.uri = uri;
            this.uriVariables = uriVariables;
            return this;
        }

        public RequestSpec post(String uri, Object... uriVariables) {
            this.method = HttpMethod.POST;
            this.uri = uri;
            this.uriVariables = uriVariables;
            return this;
        }

        public RequestSpec put(String uri, Object... uriVariables) {
            this.method = HttpMethod.PUT;
            this.uri = uri;
            this.uriVariables = uriVariables;
            return this;
        }

        public RequestSpec patch(String uri, Object... uriVariables) {
            this.method = HttpMethod.PATCH;
            this.uri = uri;
            this.uriVariables = uriVariables;
            return this;
        }

        public RequestSpec delete(String uri, Object... uriVariables) {
            this.method = HttpMethod.DELETE;
            this.uri = uri;
            this.uriVariables = uriVariables;
            return this;
        }

        public RequestSpec header(String name, String value) {
            this.headers.add(name, value);
            return this;
        }

        public RequestSpec body(Object body) {
            this.body = body;
            return this;
        }

        public <T> T retrieve(Class<T> responseType) {
            validateState();
            return client.execute(method, uri, body, headers, responseType, uriVariables);
        }

        public <T> T retrieve(ParameterizedTypeReference<T> typeRef) {
            validateState();
            return client.execute(method, uri, body, headers, typeRef, uriVariables);
        }

        private void validateState() {
            if (this.method == null || this.uri == null) {
                throw new IllegalStateException("HTTP method and URI must be set before retrieve()");
            }
        }
    }
}
