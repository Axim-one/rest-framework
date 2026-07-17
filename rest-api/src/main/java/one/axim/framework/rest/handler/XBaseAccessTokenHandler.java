package one.axim.framework.rest.handler;

import jakarta.servlet.http.HttpServletRequest;
import one.axim.framework.rest.exception.UnAuthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Created by dudgh on 2017. 6. 16..
 */
public class XBaseAccessTokenHandler implements XAccessTokenParseHandler {

    private static final Logger log = LoggerFactory.getLogger(XBaseAccessTokenHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    @Value("${axim.rest.session.secret-key:#{null}}")
    private String secretKey;

    @Value("${axim.rest.session.token-expire-days:90}")
    private int tokenExpireDays;

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Override
    public String generateAccessToken(Object sessionData) {

        try {
            String json = OBJECT_MAPPER.writeValueAsString(sessionData);
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));

            String secretKey = getSecretKey();
            if (secretKey != null) {
                String signature = sign(payload, secretKey);
                return payload + "." + signature;
            }

            return payload;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate access token", e);
        }
    }

    @Override
    public <R> R parseAccessTokenAndSession(HttpServletRequest request, Class<R> cls) throws UnAuthorizedException {

        String token = request.getHeader(getAccessTokenHeader());

        if (token == null) {
            return null;
        }

        try {
            String secretKey = getSecretKey();

            if (secretKey != null) {
                int dotIndex = token.indexOf('.');
                if (dotIndex < 0) {
                    throw new UnAuthorizedException(UnAuthorizedException.INVALID_ACCESS_TOKEN);
                }

                String payload = token.substring(0, dotIndex);
                String signature = token.substring(dotIndex + 1);

                if (!MessageDigest.isEqual(
                        sign(payload, secretKey).getBytes(StandardCharsets.UTF_8),
                        signature.getBytes(StandardCharsets.UTF_8))) {
                    throw new UnAuthorizedException(UnAuthorizedException.INVALID_ACCESS_TOKEN);
                }

                byte[] decoded = Base64.getUrlDecoder().decode(payload);
                return OBJECT_MAPPER.readValue(decoded, cls);
            }

            // No secret key configured: plain Base64
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            return OBJECT_MAPPER.readValue(decoded, cls);
        } catch (UnAuthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnAuthorizedException(UnAuthorizedException.INVALID_ACCESS_TOKEN);
        }
    }

    private String sign(String payload, String secretKey) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    @PostConstruct
    void warnIfSecretKeyMissing() {
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("axim.rest.session.secret-key is not configured. "
                    + "Access tokens are issued as unsigned plain Base64 and can be forged by anyone. "
                    + "Configure a secret key before deploying to production.");
        }
    }

    /**
     * 토큰 서명에 쓸 secret key를 반환한다. 미설정이면 null.
     * 서브클래스가 오버라이드해 다른 소스에서 공급할 수 있다.
     */
    protected String getSecretKey() {
        return secretKey;
    }

    @Override
    public int getTokenExpireDays() {
        return tokenExpireDays;
    }
}
