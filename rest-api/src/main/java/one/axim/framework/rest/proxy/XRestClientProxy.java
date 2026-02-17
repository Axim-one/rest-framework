package one.axim.framework.rest.proxy;

import one.axim.framework.core.data.XOrder;
import one.axim.framework.core.data.XPagination;
import one.axim.framework.rest.annotation.XHttpMethod;
import one.axim.framework.rest.annotation.XRestAPI;
import one.axim.framework.rest.annotation.XRestService;
import one.axim.framework.rest.configuration.XRestEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class XRestClientProxy implements InvocationHandler {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${x.rest.gateway.host:@null}")
    private String gatewayHost;

    @Value("${x.rest.debug:false}")
    private boolean isDebug;

    private final ConcurrentHashMap<String, XRestClient> clientCache = new ConcurrentHashMap<>();

    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {

        if (Object.class.equals(method.getDeclaringClass())) {
            try {
                return method.invoke(this, objects);
            } catch (Throwable t) {
                throw t;
            }
        }

        XRestService service = method.getDeclaringClass().getAnnotation(XRestService.class);

        XRestClient client = getOrCreateClient(service);

        XRestAPI api = getMethodApiAnnotation(method);

        if (api == null) return null;

        String path = api.value();

        Class<?> returnValue = method.getReturnType();

        final Type retSuperClass = method.getGenericReturnType();
        ParameterizedTypeReference<?> typeReference = null;

        if (retSuperClass instanceof ParameterizedType parameterizedType) {
            Type[] genericClasss = parameterizedType.getActualTypeArguments();

            if (genericClasss != null && genericClasss.length > 0) {

                typeReference = ParameterizedTypeReference.forType(retSuperClass);
            }
        }

        // Request Data
        HashMap<String, String> pathVariableMap = new HashMap<>();
        HashMap<String, String> parameterMap = new HashMap<>();
        List<String> sortParams = new ArrayList<>();
        HttpHeaders headers = new HttpHeaders();

        Object requestObject = null;

        Parameter[] parameters = method.getParameters();

        int paramIndex = 0;

        for (Parameter parameter : parameters) {
            Annotation[] paramAnnotation = parameter.getAnnotations();

            Object value = objects[paramIndex];
            if (value != null) {

                if (value instanceof XPagination pagination) { // PAGE Parameter Make

                    if (pagination.getSize() > 0)
                        parameterMap.put("size", String.valueOf(pagination.getSize()));

                    parameterMap.put("page", String.valueOf(pagination.getPage()));

                    if (pagination.getOffset() > 0)
                        parameterMap.put("offset", String.valueOf(pagination.getOffset()));

                    if (pagination.getOrders() != null) {
                        for (XOrder xOrder : pagination.getOrders()) {
                            sortParams.add("sort=" + xOrder.getColumn() + "," + xOrder.getDirection().name());
                        }
                    }
                } else {

                    for (Annotation annotation : paramAnnotation) {

                        if (annotation instanceof RequestParam param) {
                            parameterMap.put(param.value(), toParameterString(value));
                        } else if (annotation instanceof PathVariable param) {
                            pathVariableMap.put(param.value(), toParameterString(value));
                        } else if (annotation instanceof RequestHeader param) {
                            headers.add(param.value(), toParameterString(value));
                        } else if (annotation instanceof RequestBody) {
                            requestObject = value;
                        }
                    }
                }
            }

            paramIndex++;
        }

        XHttpMethod httpMethod = api.method();

        if (!sortParams.isEmpty()) {
            String separator = path.contains("?") ? "&" : "?";
            path += separator + String.join("&", sortParams);
        }

        if (typeReference != null) {
            return switch (httpMethod) {
                case GET -> requestObject != null
                        ? client.get(path, typeReference, requestObject, headers, pathVariableMap)
                        : client.get(path, typeReference, headers, pathVariableMap, parameterMap);
                case POST -> requestObject != null
                        ? client.post(path, typeReference, requestObject, headers, pathVariableMap)
                        : client.post(path, typeReference, parameterMap, headers, pathVariableMap);
                case PUT -> requestObject != null
                        ? client.put(path, typeReference, requestObject, headers, pathVariableMap)
                        : client.put(path, typeReference, parameterMap, headers, pathVariableMap);
                case PATCH -> requestObject != null
                        ? client.patch(path, typeReference, requestObject, headers, pathVariableMap)
                        : client.patch(path, typeReference, parameterMap, headers, pathVariableMap);
                case DELETE -> client.delete(path, typeReference, headers, pathVariableMap, parameterMap);
            };
        } else {
            return switch (httpMethod) {
                case GET -> client.get(path, returnValue, headers, pathVariableMap, parameterMap);
                case POST -> requestObject != null
                        ? client.post(path, returnValue, requestObject, headers, pathVariableMap)
                        : client.post(path, returnValue, parameterMap, headers, pathVariableMap);
                case PUT -> requestObject != null
                        ? client.put(path, returnValue, requestObject, headers, pathVariableMap)
                        : client.put(path, returnValue, parameterMap, headers, pathVariableMap);
                case PATCH -> requestObject != null
                        ? client.patch(path, returnValue, requestObject, headers, pathVariableMap)
                        : client.patch(path, returnValue, parameterMap, headers, pathVariableMap);
                case DELETE -> client.delete(path, returnValue, headers, pathVariableMap, parameterMap);
            };
        }
    }

    private XRestClient getOrCreateClient(XRestService service) {
        String cacheKey = service.host() + "|" + service.value() + "|" + service.version();

        return clientCache.computeIfAbsent(cacheKey, key -> {
            String host = service.host();
            XRestClient client;

            if (!StringUtils.hasText(host)) {
                // Gateway mode — service name/version become URL path segments for routing
                client = new XRestClient(gatewayHost, service.value(), service.version(), restTemplate);
            } else {
                if (host.contains("${")) {
                    host = XRestEnvironment.getInstance().resolvePlaceholders(host);
                }
                // Direct host mode — host points directly to the service
                client = new XRestClient(host, service.value(), restTemplate);
            }

            client.setDebug(isDebug);
            return client;
        });
    }

    private XRestAPI getMethodApiAnnotation(Method method) {
        Annotation[] annotations = method.getDeclaredAnnotations();

        for (Annotation annotation : annotations) {
            if (annotation instanceof XRestAPI api) {
                return api;
            }
        }

        return null;
    }

    private String toParameterString(Object obj) {
        if (isArray(obj)) {
            return StringUtils.arrayToDelimitedString((Object[]) obj, ",");
        }
        if (obj instanceof Collection<?> col) {
            return StringUtils.collectionToDelimitedString(col, ",");
        }
        return obj.toString();
    }

    private boolean isArray(Object obj)
    {
        return obj!=null && obj.getClass().isArray();
    }
}
