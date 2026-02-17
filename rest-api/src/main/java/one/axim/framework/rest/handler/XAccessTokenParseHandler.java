package one.axim.framework.rest.handler;

import one.axim.framework.rest.exception.UnAuthorizedException;
import one.axim.framework.rest.model.SessionData;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Created by dudgh on 2017. 6. 16..
 */
public interface XAccessTokenParseHandler {

    String ACCESS_TOKEN_HEADER = "Access-Token";

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
