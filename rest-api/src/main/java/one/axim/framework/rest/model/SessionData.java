package one.axim.framework.rest.model;

import one.axim.framework.rest.configuration.XRestEnvironment;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by dudgh on 2017. 6. 16..
 */
public class SessionData {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final int DEFAULT_TOKEN_EXPIRE_DAYS = 90;

    private String sessionId;

    private String createDate;

    public SessionData() {
        this.createDate = LocalDateTime.now().format(FORMAT);
    }

    public SessionData(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    public String getSessionId() {

        return sessionId;
    }

    public void setSessionId(String sessionId) {

        this.sessionId = sessionId;
    }

    public String getCreateDate() {

        return createDate;
    }

    public void setCreateDate(String createDate) {

        this.createDate = createDate;
    }

    @JsonIgnore
    public boolean isExpire() {

        int nDay = DEFAULT_TOKEN_EXPIRE_DAYS;
        XRestEnvironment env = XRestEnvironment.getInstance();
        if (env != null) {
            Integer configured = env.getIntValue("x.rest.session.token-expire-days");
            if (configured != null) {
                nDay = configured;
            }
        }

        try {
            LocalDateTime tokenDt = LocalDateTime.parse(this.createDate, FORMAT);
            return tokenDt.plusDays(nDay).isBefore(LocalDateTime.now());
        } catch (Exception e) {
            return true;
        }
    }
}
