package one.axim.framework.rest.handler;

import one.axim.framework.rest.exception.XRestException;
import one.axim.framework.rest.model.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

/**
 * Axim ApiError 포맷 기본 파서.
 * 커스텀 핸들러가 없거나 null 반환 시 이 핸들러로 폴백.
 */
public class DefaultErrorResponseHandler implements XErrorResponseHandler {

    private final ObjectMapper objectMapper;

    public DefaultErrorResponseHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public XRestException handle(HttpStatus status, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new XRestException(status,
                    new ApiError(status, status.getReasonPhrase(), null, false));
        }

        try {
            ApiError error = objectMapper.readValue(responseBody, ApiError.class);
            return new XRestException(status, error, responseBody);
        } catch (Exception e) {
            return new XRestException(status,
                    new ApiError(status, responseBody, e, false), responseBody);
        }
    }
}
