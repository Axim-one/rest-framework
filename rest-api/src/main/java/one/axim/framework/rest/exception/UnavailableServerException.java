package one.axim.framework.rest.exception;

import org.springframework.http.HttpStatus;

public class UnavailableServerException extends XRestException {
    public static final ErrorCode UNAVAILABLE_SERVICE = new ErrorCode("900", "server.http.error.no-response-server");
    private static final long serialVersionUID = 1L;

    public UnavailableServerException(ErrorCode error) {
        super(HttpStatus.GATEWAY_TIMEOUT, error);
    }

    public UnavailableServerException(ErrorCode error, String serviceName) {
        super(HttpStatus.GATEWAY_TIMEOUT, error, serviceName);
    }
}