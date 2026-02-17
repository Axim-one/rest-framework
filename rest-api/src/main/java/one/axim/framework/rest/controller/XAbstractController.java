package one.axim.framework.rest.controller;

import one.axim.framework.rest.exception.InvalidRequestParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

public class XAbstractController {
    private static final Logger LOGGER = LoggerFactory.getLogger(XAbstractController.class);

    @Autowired
    protected HttpServletRequest httpServletRequest;

    protected void log(String message) {

        LOGGER.info(message);
    }

    protected void error(String message, Throwable t) {

        LOGGER.error(message, t);
    }

    protected String getOptionParameter(String name) {

        return httpServletRequest.getParameter(name);
    }

    protected String getParameter(String name) {

        String value = httpServletRequest.getParameter(name);

        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestParameterException(
                    InvalidRequestParameterException.INVALID_REQUEST_PARAMETER,
                    "Not found " + name + " parameter");
        }

        return value;
    }

    public int getIntParameter(String name) {
        try {
            return Integer.parseInt(getParameter(name));
        } catch (NumberFormatException e) {
            throw new InvalidRequestParameterException(
                    InvalidRequestParameterException.INVALID_REQUEST_PARAMETER,
                    "Parameter '" + name + "' is not a valid integer");
        }
    }

    public int getIntParameter(String name, int defaultValue) {
        String value = getOptionParameter(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new InvalidRequestParameterException(
                    InvalidRequestParameterException.INVALID_REQUEST_PARAMETER,
                    "Parameter '" + name + "' is not a valid integer");
        }
    }

    public String getStringParameter(String name) {

        return getParameter(name);
    }

    public String getStringParameter(String name, String defaultValue) {

        String value = getOptionParameter(name);

        if (value == null) {
            return defaultValue;
        }

        return value;
    }

    public String getHeader(String name) {

        return httpServletRequest.getHeader(name);
    }
}
