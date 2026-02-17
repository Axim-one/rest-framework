package one.axim.framework.rest.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends XRestException {
    public static final ErrorCode NOT_FOUND_API = new ErrorCode("100", "server.http.error.notfound-api");
    private static final long serialVersionUID = 1L;

    public NotFoundException(ErrorCode error) {
        super(HttpStatus.NOT_FOUND, error);
    }
}