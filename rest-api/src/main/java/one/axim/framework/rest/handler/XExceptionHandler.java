package one.axim.framework.rest.handler;

import one.axim.framework.rest.exception.XRestException;
import one.axim.framework.rest.model.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by dudgh on 2017. 6. 16..
 */
@RestControllerAdvice
public class XExceptionHandler {
    private final Logger log = LoggerFactory.getLogger(XExceptionHandler.class);
    private final boolean isDebug;

    private MessageSource messageSource;

    private final String defaultLanguageCode;
    private final String languageHeader;

    public XExceptionHandler(@Autowired ApplicationContext applicationContext) {

        Environment environment = applicationContext.getEnvironment();

        String[] profiles = environment.getActiveProfiles();

        defaultLanguageCode = environment.getProperty("axim.rest.message.default-language", "ko-KR");
        languageHeader = environment.getProperty("axim.rest.message.language-header", "Accept-Language");

        boolean isProd = false;
        if (profiles != null) {
            for (String profile : profiles) {
                if (profile.equals("prod")) {
                    isProd = true;
                    break;
                }
            }
        }

        try {
            messageSource = applicationContext.getBean(MessageSource.class);
        } catch (BeansException e) {
            // not found bean ..
            messageSource = null;
        }

        this.isDebug = !isProd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> processPostBodyError(HttpMessageNotReadableException ex, HttpServletRequest request) {
        exceptionLog(ex);

        ApiError errorModel = new ApiError(
                HttpStatus.BAD_REQUEST,
                getMessage("server.http.error.invalid-parameter", request), ex, this.isDebug);
        errorModel.setDescription(ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorModel);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> processNotSupportedError(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        exceptionLog(ex);

        ApiError errorModel = new ApiError(
                HttpStatus.METHOD_NOT_ALLOWED,
                getMessage("server.http.error.not-support-method", request), ex, this.isDebug);
        errorModel.setDescription(ex.getMessage());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(errorModel);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> processParameterError(MissingServletRequestParameterException ex, HttpServletRequest request) {
        exceptionLog(ex);

        String message = ex.getParameterName() + " " + ex.getMessage();

        ApiError errorModel = new ApiError(HttpStatus.BAD_REQUEST,
                getMessage("server.http.error.invalid-parameter", request), ex, this.isDebug);
        errorModel.setDescription(message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorModel);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> processNotfoundError(NoHandlerFoundException ex, HttpServletRequest request) {
        exceptionLog(ex);

        ApiError errorModel = new ApiError(HttpStatus.NOT_FOUND,
                getMessage("server.http.error.notfound-api", request), ex, false);
        errorModel.setDescription(ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorModel);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> processParameterTypeError(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        exceptionLog(ex);

        String message = ex.getParameter().getParameterName() + " " + ex.getMessage();

        ApiError errorModel = new ApiError(HttpStatus.BAD_REQUEST,
                getMessage("server.http.error.invalid-parameter", request), ex, this.isDebug);
        errorModel.setDescription(message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorModel);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> processBindingTypeError(BindException ex, HttpServletRequest request) {
        exceptionLog(ex);

        ApiError errorModel = new ApiError(HttpStatus.BAD_REQUEST,
                getMessage("server.http.error.invalid-parameter", request), ex, this.isDebug);
        errorModel.setDescription(ex.getMessage());

        List<Map<String, Object>> errorFields = new ArrayList<>();
        for (FieldError error : ex.getFieldErrors()) {
            errorFields.add(Map.of(
                    "field", error.getField(),
                    "errorMessage", getMessage(error.getDefaultMessage(), request)
            ));
        }
        errorModel.setData(errorFields);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorModel);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> processValidationError(MethodArgumentNotValidException ex, HttpServletRequest request) {
        exceptionLog(ex);

        List<Map<String, Object>> errorFields = new ArrayList<>();
        List<FieldError> errors = ex.getBindingResult().getFieldErrors();

        String description = getMessage("server.http.error.invalid-parameter", request);

        for (FieldError error : errors) {
            errorFields.add(Map.of(
                    "field", error.getField(),
                    "errorMessage", getMessage(error.getDefaultMessage(), request)
            ));
        }

        if(!errors.isEmpty()) {
            if(StringUtils.hasText(errors.get(0).getDefaultMessage())) {
                description = getMessage(errors.get(0).getDefaultMessage(), request);
            }
        }

        ApiError errorModel = new ApiError(HttpStatus.UNPROCESSABLE_ENTITY,
                description, ex, this.isDebug);
        errorModel.setDescription(ex.getMessage());
        errorModel.setData(errorFields);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errorModel);
    }

    @ExceptionHandler(XRestException.class)
    public ResponseEntity<ApiError> handleRestException(XRestException xe, HttpServletRequest request) {
        exceptionLog(xe);

        ApiError error = new ApiError(xe, this.isDebug);
        error.setMessage(getMessage(xe.getMessage(), xe.getArgs(), request));

        if(xe.getData() != null) {
            error.setData(xe.getData());
        }

        return ResponseEntity.status(xe.getStatus()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleBaseException(Exception e, HttpServletRequest request) {
        exceptionLog(e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, getMessage("server.http.error.server-error", request), e, this.isDebug));
    }

    private void exceptionLog(Exception ex) {
        log.error(ex.getMessage(), ex);
    }

    private String getMessage(String code, HttpServletRequest request) {
        return getMessage(code, null, request);
    }

    private String getMessage(String code, Object[] arg, HttpServletRequest request) {
        if (messageSource != null && code != null) {
            return messageSource.getMessage(code, arg, code, getLanguage(request, Locale.forLanguageTag(defaultLanguageCode)));
        }
        return code;
    }

    private Locale getLanguage(HttpServletRequest request, Locale defaultLocale) {
        String contentLanguage = request.getHeader(languageHeader);

        if (contentLanguage == null || contentLanguage.isEmpty()) {
            contentLanguage = defaultLanguageCode;
        }

        Locale val = Locale.forLanguageTag(contentLanguage);

        if (val != null && !val.getLanguage().isEmpty()) {
            return val;
        }

        return defaultLocale;
    }
}