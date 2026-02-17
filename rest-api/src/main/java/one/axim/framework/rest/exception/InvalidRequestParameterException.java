package one.axim.framework.rest.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestParameterException extends XRestException {
    public static final ErrorCode INVALID_REQUEST_PARAMETER = new ErrorCode("11", "server.http.error.invalid-parameter");
    public static final ErrorCode NOT_FOUND_REQUEST_BODY = new ErrorCode("12", "server.http.error.notfound-api");
    public static final ErrorCode NOT_SUPPORT_METHOD = new ErrorCode("13", "server.http.error.not-support-method");
    private static final long serialVersionUID = 1L;

    public InvalidRequestParameterException(ErrorCode error) {
        super(HttpStatus.BAD_REQUEST, error);
    }

    public InvalidRequestParameterException(ErrorCode error, String description) {
        super(HttpStatus.BAD_REQUEST, error, description);
    }
}