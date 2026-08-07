package one.axim.framework.rest.configuration;

import one.axim.framework.rest.handler.XAccessTokenParseHandler;
import one.axim.framework.rest.model.SessionData;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * 기동 시 세션 인증 설정을 검증한다.
 *
 * <p>{@link SessionData}를 파라미터로 받는 컨트롤러 메서드가 있는데
 * {@link XAccessTokenParseHandler} 빈이 등록되지 않았다면, 해당 파라미터는
 * 런타임에 조용히 {@code null}로 주입되어 인증 없이 요청이 처리된다.
 * 이 검증기는 그 상황을 기동 시점에 실패시킨다.</p>
 */
@Component
public class XSessionConfigurationValidator implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {

        ApplicationContext context = event.getApplicationContext();

        if (!context.getBeansOfType(XAccessTokenParseHandler.class).isEmpty()) {
            return;
        }

        // ⚠️ getIfAvailable() 은 후보가 2개 이상이면 null 이 아니라 NoUniqueBeanDefinitionException 을 던진다.
        //   Actuator 가 있으면 controllerEndpointHandlerMapping 이 함께 잡혀 후보가 2개가 되고,
        //   컨트롤러가 없는 배치 서비스(핸들러 빈 미등록 → 위 early-return 미적용)가 기동 실패한다.
        //   검증을 못 하는 상황은 통과시키는 게 맞으므로 getIfUnique() 로 바꾼다. (v1.4.2)
        RequestMappingHandlerMapping mapping =
                context.getBeanProvider(RequestMappingHandlerMapping.class).getIfUnique();

        if (mapping == null) {
            return;
        }

        List<String> offenders = new ArrayList<>();

        for (HandlerMethod handlerMethod : mapping.getHandlerMethods().values()) {
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                if (SessionData.class.isAssignableFrom(parameter.getParameterType())) {
                    offenders.add(handlerMethod.getBeanType().getName()
                            + "#" + handlerMethod.getMethod().getName());
                    break;
                }
            }
        }

        if (!offenders.isEmpty()) {
            throw new IllegalStateException(
                    "No XAccessTokenParseHandler bean is registered, but the following controller methods "
                            + "declare a SessionData parameter: " + String.join(", ", offenders)
                            + ". Without a handler these parameters resolve to null and the requests would be "
                            + "processed without authentication. Register an XAccessTokenParseHandler bean "
                            + "(e.g. a @Component extending XBaseAccessTokenHandler).");
        }
    }
}
