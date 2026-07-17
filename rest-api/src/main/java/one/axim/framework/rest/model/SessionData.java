package one.axim.framework.rest.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by dudgh on 2017. 6. 16..
 */
public class SessionData {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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

    /**
     * 토큰 생성 시각으로부터 {@code expireDays}일이 지났는지 판단한다.
     *
     * @param expireDays 토큰 유효 일수
     * @return 만료되었거나 {@code createDate}를 해석할 수 없으면 true
     */
    @JsonIgnore
    public boolean isExpire(int expireDays) {
        try {
            LocalDateTime tokenDt = LocalDateTime.parse(this.createDate, FORMAT);
            return tokenDt.plusDays(expireDays).isBefore(LocalDateTime.now());
        } catch (Exception e) {
            return true;
        }
    }
}
