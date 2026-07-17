package one.axim.framework.rest.filters;

import one.axim.framework.rest.configuration.XRestEnvironment;
import one.axim.framework.rest.handler.XRequestWrapper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class XRequestFilter implements Filter {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "access-token", "x-api-key", "proxy-authorization"
    );

    private static final Set<String> SENSITIVE_PARAMS = Set.of(
            "password", "passwd", "pwd", "secret", "token",
            "access-token", "accesstoken", "api-key", "apikey", "credential"
    );

    private final XRestEnvironment environment;

    public XRequestFilter(XRestEnvironment environment) {
        this.environment = environment;
    }

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${spring.application.version:unknown}")
    private String applicationVersion;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) servletRequest;

        try {
            String uuid = UUID.randomUUID().toString();

            MDC.put("UUID", uuid);
            MDC.put("URL", req.getRequestURI());
            MDC.put("METHOD", req.getMethod());

            MDC.put("SERVICE_ID", applicationName);
            MDC.put("VERSION", applicationVersion);

            MDC.put("LOCAL_IP", environment.getServerIp());
            MDC.put("LOCAL_HOSTNAME", environment.getServerHostName());
            MDC.put("REMOTE_IP", req.getRemoteAddr());

            // REQUEST HEADER
            Enumeration<String> names = req.getHeaderNames();
            if (names != null) {

                StringBuilder headerInfoBuilder = new StringBuilder();
                while (names.hasMoreElements()) {
                    String name = names.nextElement();
                    String value = SENSITIVE_HEADERS.contains(name.toLowerCase()) ? "***" : req.getHeader(name);

                    headerInfoBuilder.append(String.format("%s = > %s\n", name, value));
                }

                MDC.put("HEADER", headerInfoBuilder.toString().trim());
            }

            String contentType = req.getHeader("content-type");

            MDC.put("PARAMETER", getRequestParameterString(req));

            if (environment.isDevelop()) {

                if (contentType != null && contentType.startsWith("application/json")) {

                    XRequestWrapper requestWrapper = new XRequestWrapper(req);

                    String body = requestWrapper.getRequestBody();

                    if (body != null && !body.isEmpty()) {
                        MDC.put("PARAMETER", body);
                    }

                    filterChain.doFilter(requestWrapper, servletResponse);

                } else {

                    filterChain.doFilter(servletRequest, servletResponse);
                }
            } else {
                filterChain.doFilter(servletRequest, servletResponse);
            }
        } finally {
            MDC.clear();
        }
    }

    private String getRequestParameterString(HttpServletRequest request) {

        final StringBuilder paramSb = new StringBuilder();
        Map<String, String[]> params = request.getParameterMap();

        params.forEach((name, values) -> {
            String value = SENSITIVE_PARAMS.contains(name.toLowerCase())
                    ? "***"
                    : String.join(", ", values);

            paramSb.append(String.format("%s => %s, ", name, value));
        });

        return paramSb.toString();
    }
}
