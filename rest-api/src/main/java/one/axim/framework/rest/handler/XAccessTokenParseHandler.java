package one.axim.framework.rest.handler;

import one.axim.framework.rest.exception.UnAuthorizedException;
import one.axim.framework.rest.model.SessionData;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Created by dudgh on 2017. 6. 16..
 */
public interface XAccessTokenParseHandler {

    String DEFAULT_ACCESS_TOKEN_HEADER = "Access-Token";

    /**
     * 인증 토큰을 읽어올 HTTP 헤더 이름을 반환합니다.
     * 기본값은 {@code "Access-Token"}이며, 구현체에서 override하여 변경할 수 있습니다.
     *
     * @return 헤더 이름
     */
    default String getAccessTokenHeader() {
        return DEFAULT_ACCESS_TOKEN_HEADER;
    }

    String generateAccessToken(Object sessionData);

    <R> R parseAccessTokenAndSession(HttpServletRequest request, Class<R> cls) throws UnAuthorizedException;

    default <R extends SessionData> R validateSession(HttpServletRequest request, Class<R> cls) throws UnAuthorizedException {
        R session = parseAccessTokenAndSession(request, cls);

        if (session == null) {
            throw new UnAuthorizedException(UnAuthorizedException.NOT_FOUND_ACCESS_TOKEN);
        }

        request.setAttribute("sessionId", session.getSessionId());

        if (session.isExpire()) {
            throw new UnAuthorizedException(UnAuthorizedException.EXPIRE_ACCESS_TOKEN);
        }

        return session;
    }
}
