package one.axim.framework.rest.exception;

import org.springframework.http.HttpStatus;

public class UnknownServerException extends XRestException {
    public static final ErrorCode UNKNOWN_SERVER_EXCEPTION = new ErrorCode("999", "server.http.error.unknown-server-error");
    private static final long serialVersionUID = 1L;

    public UnknownServerException(ErrorCode error) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, error);
    }

    public UnknownServerException(ErrorCode error, String description) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, error, description);
    }
}