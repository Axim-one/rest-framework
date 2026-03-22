package one.axim.framework.rest.exception;

import com.fasterxml.jackson.annotation.JsonIgnore;
import one.axim.framework.rest.model.ApiError;
import org.springframework.http.HttpStatus;

/**
 * Created by dudgh on 2017. 6. 16..
 */
public class XRestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    protected final HttpStatus status;
    protected String code;
    protected String message;
    protected String description;
    protected Object[] args;
    protected Object data;

    /** 원본 응답 바디 (항상 보존). 서버 측 로그/디버깅 전용 */
    @JsonIgnore
    protected String rawResponseBody;

    /** 원격 서비스명 (프록시 경유 시 자동 설정) */
    @JsonIgnore
    protected String remoteServiceName;

    protected XRestException(HttpStatus status) {
        super();
        this.status = status;
    }

    protected XRestException(HttpStatus status, ErrorCode error) {
        super(error.messageKey());
        this.status = status;
        this.code = error.code();
        this.message = error.messageKey();
    }

    protected XRestException(HttpStatus status, ErrorCode error, String description) {
        super(error.messageKey());
        this.status = status;
        this.code = error.code();
        this.message = error.messageKey();
        this.description = description;
    }

    protected XRestException(HttpStatus status, ErrorCode error, Object[] args) {
        super(error.messageKey());
        this.status = status;
        this.code = error.code();
        this.message = error.messageKey();
        this.args = args;
    }

    protected XRestException(HttpStatus status, ErrorCode error, Throwable cause) {
        super(error.messageKey(), cause);
        this.status = status;
        this.code = error.code();
        this.message = error.messageKey();
    }

    public XRestException(HttpStatus status, ApiError error) {
        super(error.getMessage());
        this.status = status;
        this.code = error.getCode();
        this.message = error.getMessage();
        this.description = error.getDescription();
        this.data = error.getData();
    }

    public XRestException(HttpStatus status, ApiError error, String rawResponseBody) {
        this(status, error);
        this.rawResponseBody = rawResponseBody;
    }

    public Object[] getArgs() {
        return args;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getRawResponseBody() {
        return rawResponseBody;
    }

    public void setRawResponseBody(String rawResponseBody) {
        this.rawResponseBody = rawResponseBody;
    }

    public String getRemoteServiceName() {
        return remoteServiceName;
    }

    public void setRemoteServiceName(String remoteServiceName) {
        this.remoteServiceName = remoteServiceName;
    }
}