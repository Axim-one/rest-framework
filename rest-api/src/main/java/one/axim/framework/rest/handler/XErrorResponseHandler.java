package one.axim.framework.rest.handler;

import one.axim.framework.rest.exception.XRestException;
import org.springframework.http.HttpStatus;

/**
 * 서비스별 에러 응답 파싱 핸들러.
 * {@code @XRestService(value)} + "-error-handler" 이름의 Bean으로 등록하면 자동 매칭.
 *
 * <pre>
 * // 예시: @XRestService(value = "stripe-api") 에 매칭
 * {@literal @}Component("stripe-api-error-handler")
 * public class StripeErrorHandler implements XErrorResponseHandler { ... }
 * </pre>
 */
@FunctionalInterface
public interface XErrorResponseHandler {

    /**
     * 에러 응답을 파싱하여 XRestException으로 변환.
     *
     * @param status       HTTP 상태 코드
     * @param responseBody 원본 응답 바디 문자열
     * @return 변환된 XRestException (null 반환 시 기본 핸들러로 폴백)
     */
    XRestException handle(HttpStatus status, String responseBody);
}
