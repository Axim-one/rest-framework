package one.axim.framework.rest.model;

import one.axim.framework.rest.exception.ErrorCode;
import one.axim.framework.rest.exception.XRestException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by dudgh on 2017. 6. 16..
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApiError {

    /**
     * 에러 코드
     */
    private String code;

    /**
     * 에러 메시지
     */
    private String message;

    /**
     * 에러에 대한 추가적인 상세 설명 (optional)
     */
    private String description;

    /**
     * 에러에 대한 추가적인 첨부 데이터 (optional)
     */
    private Object data;

    /**
     * 에러 Exception Stak ( only debug )
     */
    private String stackTrace;

    public ApiError() {
    }

    public ApiError(ErrorCode errorCode, Exception e, boolean isDebug) {
        this.code = errorCode.code();
        this.message = errorCode.messageKey();

        if (isDebug) {
            displayErrorStack(e);
        }
    }

    public ApiError(HttpStatus code, String message, Exception e, boolean isDebug) {

        this.code = String.valueOf(code.value());
        this.message = message;
        if (isDebug) {
            displayErrorStack(e);
        }
    }

    public ApiError(XRestException e, boolean isDebug) {

        this.code = e.getCode();
        this.message = e.getMessage();
        this.description = e.getDescription();

        if (isDebug) {
            displayErrorStack(e);
        }
    }

    private void displayErrorStack(Exception e) {
        StringWriter errors = new StringWriter();
        e.printStackTrace(new PrintWriter(errors));
        this.stackTrace = errors.toString();
    }

    public Object getData() {

        return data;
    }

    public void setData(Object data) {

        this.data = data;
    }

    public String getCode() {

        return code;
    }

    public void setCode(String code) {

        this.code = code;
    }

    public String getMessage() {

        return message;
    }

    public void setMessage(String message) {

        this.message = message;
    }

    public String getDescription() {

        return description;
    }

    public void setDescription(String description) {

        this.description = description;
    }

    public String getStackTrace() {

        return stackTrace;
    }
}