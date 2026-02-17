package one.axim.framework.rest.exception;

import org.springframework.http.HttpStatus;

public class UnAuthorizedException extends XRestException {
    public static final ErrorCode NOT_FOUND_ACCESS_TOKEN = new ErrorCode("1", "server.http.error.required-auth");
    public static final ErrorCode INVALID_ACCESS_TOKEN = new ErrorCode("2", "server.http.error.invalid-auth");
    public static final ErrorCode EXPIRE_ACCESS_TOKEN = new ErrorCode("3", "server.http.error.expire-auth");
    private static final long serialVersionUID = 1L;

    public UnAuthorizedException(ErrorCode error) {
        super(HttpStatus.UNAUTHORIZED, error);
    }

    public UnAuthorizedException(ErrorCode error, String description) {
        super(HttpStatus.UNAUTHORIZED, error, description);
    }
}