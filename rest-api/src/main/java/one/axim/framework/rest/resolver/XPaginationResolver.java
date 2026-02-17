package one.axim.framework.rest.resolver;

import one.axim.framework.core.annotation.XPaginationDefault;
import one.axim.framework.core.data.XDirection;
import one.axim.framework.core.data.XOrder;
import one.axim.framework.core.data.XPagination;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Created by dudgh on 2017. 6. 13..
 */
public class XPaginationResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {

        return parameter.getParameterType().equals(XPagination.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        XPaginationDefault xPaginationDefault = parameter.getParameterAnnotation(XPaginationDefault.class);

        XPagination returnValue = new XPagination();

        int offsetDefault = 0;
        int sizeDefault = 0;

        String sortColumnDefault = null;
        XDirection sortDirectionDefault = XDirection.DESC;

        if (xPaginationDefault != null) {
            offsetDefault = xPaginationDefault.offset();
            sizeDefault = xPaginationDefault.size();

            sortColumnDefault = xPaginationDefault.column().isEmpty() ? null : xPaginationDefault.column();
            sortDirectionDefault = xPaginationDefault.direction();
        }

        returnValue.setOffset(getParameterIntValue("offset", request, offsetDefault));
        returnValue.setSize(getParameterIntValue("size", request, sizeDefault));

        if (request.getParameter("sort") != null) {
            String[] sortValues = request.getParameterValues("sort");
            for (String sortValue : sortValues) {
                String[] orderValue = sortValue.split("\\,");
                if (orderValue.length > 1) {
                    returnValue.addOrder(
                            new XOrder(orderValue[0].trim(), XDirection.fromString(orderValue[1].trim())));
                } else if (orderValue.length == 1 && !orderValue[0].trim().isEmpty()) {
                    returnValue.addOrder(new XOrder(orderValue[0].trim(), XDirection.ASC));
                }
            }
        } else {
            if (sortColumnDefault != null) {
                returnValue.addOrder(new XOrder(sortColumnDefault, sortDirectionDefault));
            }
        }

        if (hasParameter("page", request) ||
                (xPaginationDefault != null && !hasParameter("offset", request) && xPaginationDefault.page() != 0))
            returnValue.setPage(getParameterIntValue("page", request, 1));


        return returnValue;
    }


    private int getParameterIntValue(String name, HttpServletRequest request, int defaultValue) {

        String value = request.getParameter(name);

        if ("undefined".equals(value)) {
            value = null;
        }

        if ("null".equals(value)) {
            value = null;
        }

        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean hasParameter(String name, HttpServletRequest request) {
        String value = request.getParameter(name);
        return StringUtils.hasText(value);
    }

}