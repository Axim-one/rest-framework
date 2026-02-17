package one.axim.framework.rest.handler;

import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface IXRequestHandler {

    void onPreControllerHandler(HttpServletRequest request, HttpServletResponse response, Object handler);

    void onPostControllerHandler(HttpServletRequest request, HttpServletResponse response, Object handler,
                                 ModelAndView modelAndView);

    void onCompleteControllerHandler(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex);
}
