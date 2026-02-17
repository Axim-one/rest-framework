package one.axim.framework.rest.proxy;

import one.axim.framework.rest.exception.UnavailableServerException;
import one.axim.framework.rest.exception.XRestException;
import one.axim.framework.rest.model.ApiError;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.stream.Collectors;

public class XRestClient {

    private static final Logger logger = LoggerFactory.getLogger(XRestClient.class);
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private final RestTemplate restTemplate;
    private final String serviceName;
    private final String serviceHost;
    private boolean isDebug = false;

    public XRestClient(String host, String serviceName, RestTemplate restTemplate) {
        this.serviceName = serviceName;
        this.restTemplate = restTemplate;
        this.serviceHost = host;
    }

    public XRestClient(String host, String serviceName, String version, RestTemplate restTemplate) {
        this.serviceName = serviceName;
        this.restTemplate = restTemplate;

        StringBuilder sb = new StringBuilder();
        sb.append(host);

        if (!serviceName.isEmpty()) {
            sb.append("/").append(serviceName);
        }

        if (!version.isEmpty()) {
            sb.append("/").append(version);
        }

        this.serviceHost = sb.toString();
    }

    // ──────────────────────────────────────────
    // GET
    // ──────────────────────────────────────────

    public <R> R get(String url, ParameterizedTypeReference<R> typeRef, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable, @Nullable Map<String, String> queryParameter) throws XRestException, IOException {
        url = appendQueryString(url, queryParameter);
        return request(url, HttpMethod.GET, typeRef, headers, null, pathVariable);
    }

    public <R> R get(String url, ParameterizedTypeReference<R> typeRef, Object requestBody, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return request(url, HttpMethod.GET, typeRef, headers, requestBody, pathVariable);
    }

    public <R> R get(String url, Class<R> typeRef, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable, @Nullable Map<String, String> queryParameter) throws XRestException, IOException {
        url = appendQueryString(url, queryParameter);
        return requestObject(url, HttpMethod.GET, typeRef, headers, null, pathVariable);
    }

    // ──────────────────────────────────────────
    // POST
    // ──────────────────────────────────────────

    public <R> R post(String url, ParameterizedTypeReference<R> typeRef, @Nullable HttpHeaders headers) throws XRestException, IOException {
        return request(url, HttpMethod.POST, typeRef, headers, null, null);
    }

    public <R> R post(String url, Class<R> typeRef, @Nullable HttpHeaders headers) throws XRestException, IOException {
        return requestObject(url, HttpMethod.POST, typeRef, headers, null, null);
    }

    public <R> R post(String url, ParameterizedTypeReference<R> typeRef, @Nullable HttpHeaders headers, Map<String, String> pathVariable) throws XRestException, IOException {
        return request(url, HttpMethod.POST, typeRef, headers, null, pathVariable);
    }

    public <R> R post(String url, Class<R> typeRef, @Nullable HttpHeaders headers, Map<String, String> pathVariable) throws XRestException, IOException {
        return requestObject(url, HttpMethod.POST, typeRef, headers, null, pathVariable);
    }

    public <R> R post(String url, ParameterizedTypeReference<R> typeRef, Object requestBody, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return request(url, HttpMethod.POST, typeRef, headers, requestBody, pathVariable);
    }

    public <R> R post(String url, Class<R> typeRef, Object requestBody, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return requestObject(url, HttpMethod.POST, typeRef, headers, requestBody, pathVariable);
    }

    public <R> R post(String url, ParameterizedTypeReference<R> typeRef, @Nullable Map<String, String> parameters, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return request(url, HttpMethod.POST, typeRef, headers, mapToMultiValueMap(parameters), pathVariable);
    }

    public <R> R post(String url, Class<R> typeRef, @Nullable Map<String, String> parameters, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return requestObject(url, HttpMethod.POST, typeRef, headers, mapToMultiValueMap(parameters), pathVariable);
    }

    // ──────────────────────────────────────────
    // PUT
    // ──────────────────────────────────────────

    public <R> R put(String url, ParameterizedTypeReference<R> typeRef, @Nullable HttpHeaders headers) throws XRestException, IOException {
        return request(url, HttpMethod.PUT, typeRef, headers, null, null);
    }

    public <R> R put(String url, Class<R> typeRef, @Nullable HttpHeaders headers) throws XRestException, IOException {
        return requestObject(url, HttpMethod.PUT, typeRef, headers, null, null);
    }

    public <R> R put(String url, ParameterizedTypeReference<R> typeRef, @Nullable HttpHeaders headers, Map<String, String> pathVariable) throws XRestException, IOException {
        return request(url, HttpMethod.PUT, typeRef, headers, null, pathVariable);
    }

    public <R> R put(String url, Class<R> typeRef, @Nullable HttpHeaders headers, Map<String, String> pathVariable) throws XRestException, IOException {
        return requestObject(url, HttpMethod.PUT, typeRef, headers, null, pathVariable);
    }

    public <R> R put(String url, ParameterizedTypeReference<R> typeRef, Object requestBody, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return request(url, HttpMethod.PUT, typeRef, headers, requestBody, pathVariable);
    }

    public <R> R put(String url, Class<R> typeRef, Object requestBody, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return requestObject(url, HttpMethod.PUT, typeRef, headers, requestBody, pathVariable);
    }

    public <R> R put(String url, ParameterizedTypeReference<R> typeRef, @Nullable Map<String, String> parameters, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return request(url, HttpMethod.PUT, typeRef, headers, mapToMultiValueMap(parameters), pathVariable);
    }

    public <R> R put(String url, Class<R> typeRef, @Nullable Map<String, String> parameters, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return requestObject(url, HttpMethod.PUT, typeRef, headers, mapToMultiValueMap(parameters), pathVariable);
    }

    // ──────────────────────────────────────────
    // PATCH
    // ──────────────────────────────────────────

    public <R> R patch(String url, ParameterizedTypeReference<R> typeRef, Object requestBody, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return request(url, HttpMethod.PATCH, typeRef, headers, requestBody, pathVariable);
    }

    public <R> R patch(String url, Class<R> typeRef, Object requestBody, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return requestObject(url, HttpMethod.PATCH, typeRef, headers, requestBody, pathVariable);
    }

    public <R> R patch(String url, ParameterizedTypeReference<R> typeRef, @Nullable Map<String, String> parameters, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return request(url, HttpMethod.PATCH, typeRef, headers, mapToMultiValueMap(parameters), pathVariable);
    }

    public <R> R patch(String url, Class<R> typeRef, @Nullable Map<String, String> parameters, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable) throws XRestException, IOException {
        return requestObject(url, HttpMethod.PATCH, typeRef, headers, mapToMultiValueMap(parameters), pathVariable);
    }

    // ──────────────────────────────────────────
    // DELETE
    // ──────────────────────────────────────────

    public <R> R delete(String url, ParameterizedTypeReference<R> typeRef, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable, @Nullable Map<String, String> queryParameter) throws XRestException, IOException {
        url = appendQueryString(url, queryParameter);
        return request(url, HttpMethod.DELETE, typeRef, headers, null, pathVariable);
    }

    public <R> R delete(String url, Class<R> typeRef, @Nullable HttpHeaders headers, @Nullable Map<String, String> pathVariable, @Nullable Map<String, String> queryParameter) throws XRestException, IOException {
        url = appendQueryString(url, queryParameter);
        return requestObject(url, HttpMethod.DELETE, typeRef, headers, null, pathVariable);
    }

    // ──────────────────────────────────────────
    // Core request method
    // ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    protected <R> R request(String path, HttpMethod method, ParameterizedTypeReference<R> typeRef,
                            @Nullable HttpHeaders headers,
                            @Nullable Object requestBody,
                            @Nullable Map<String, String> pathVarMap) throws XRestException, IOException {

        String url = makeServiceUrl(path);

        if (this.isDebug) {
            logger.info("{} request url :: {}", method.name(), url);
        }

        headers = prepareHeaders(headers, requestBody);
        HttpEntity<?> entity = new HttpEntity<>(requestBody, headers);
        Map<String, String> vars = (pathVarMap != null) ? pathVarMap : Map.of();

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, method, entity, String.class, vars);
        } catch (ResourceAccessException e) {
            throw new UnavailableServerException(UnavailableServerException.UNAVAILABLE_SERVICE, this.serviceName);
        } catch (HttpStatusCodeException e) {
            response = new ResponseEntity<>(e.getResponseBodyAsString(), e.getStatusCode());
        }

        if (this.isDebug) {
            logger.info("response status : {} body :: {}", response.getStatusCode().value(), response.getBody());
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throwRestException(response);
        }

        if (!typeRef.getType().equals(Void.TYPE)) {
            if (typeRef.getType().equals(String.class)) {
                return (R) response.getBody();
            }
            if (StringUtils.hasText(response.getBody())) {
                JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructType(typeRef.getType());
                return OBJECT_MAPPER.readValue(response.getBody(), javaType);
            }
        }

        return null;
    }

    protected <R> R requestObject(String path, HttpMethod method, Class<R> type,
                                  @Nullable HttpHeaders headers,
                                  @Nullable Object requestBody,
                                  @Nullable Map<String, String> pathVarMap) throws XRestException, IOException {
        return request(path, method, ParameterizedTypeReference.forType(type), headers, requestBody, pathVarMap);
    }

    public void setDebug(boolean debug) {
        isDebug = debug;
    }

    // ──────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private HttpHeaders prepareHeaders(@Nullable HttpHeaders headers, @Nullable Object requestBody) {
        if (headers == null) headers = new HttpHeaders();

        if (requestBody != null) {
            if (requestBody instanceof MultiValueMap) {
                headers.set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            } else {
                headers.set("Content-Type", "application/json; charset=UTF-8");
            }
        }

        return headers;
    }

    private void throwRestException(ResponseEntity<String> response) throws XRestException {
        HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
        String body = response.getBody();

        if (body == null || body.isBlank()) {
            throw new XRestException(status, new ApiError(status, status.getReasonPhrase(), null, false));
        }

        try {
            ApiError error = OBJECT_MAPPER.readValue(body, ApiError.class);
            throw new XRestException(status, error);
        } catch (XRestException e) {
            throw e;
        } catch (Exception e) {
            throw new XRestException(status, new ApiError(status, body, e, false));
        }
    }

    private String makeServiceUrl(String url) {
        if (this.serviceHost.startsWith("http://") || this.serviceHost.startsWith("https://")) {
            return this.serviceHost + (url.startsWith("/") ? "" : "/") + url;
        } else {
            return "http://" + this.serviceHost + (url.startsWith("/") ? "" : "/") + url;
        }
    }

    private String appendQueryString(String url, @Nullable Map<String, String> queryParameter) {
        if (queryParameter != null && !queryParameter.isEmpty()) {
            String separator = url.contains("?") ? "&" : "?";
            url += separator + queryString(queryParameter);
        }
        return url;
    }

    private String queryString(Map<String, String> parameter) {
        return parameter.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private MultiValueMap<String, String> mapToMultiValueMap(Map<String, String> map) {
        if (map == null) return null;

        LinkedMultiValueMap<String, String> result = new LinkedMultiValueMap<>();
        map.forEach(result::add);
        return result;
    }
}