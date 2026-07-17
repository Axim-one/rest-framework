package one.axim.framework.rest.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class SessionDataTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Test
    @DisplayName("새로 만든 세션은 만료되지 않는다")
    void isExpire_freshSession() {
        SessionData session = new SessionData("sid-1");

        assertThat(session.isExpire(90)).isFalse();
    }

    @Test
    @DisplayName("생성자는 sessionId와 createDate를 설정한다")
    void constructor_setsFields() {
        SessionData session = new SessionData("sid-1");

        assertThat(session.getSessionId()).isEqualTo("sid-1");
        assertThat(session.getCreateDate()).isNotBlank();
    }

    @Test
    @DisplayName("유효 일수를 넘긴 세션은 만료된다")
    void isExpire_oldSession() {
        SessionData session = new SessionData("sid-1");
        session.setCreateDate(LocalDateTime.now().minusDays(91).format(FORMAT));

        assertThat(session.isExpire(90)).isTrue();
    }

    @Test
    @DisplayName("유효 일수 안쪽의 세션은 만료되지 않는다")
    void isExpire_withinExpiry() {
        SessionData session = new SessionData("sid-1");
        session.setCreateDate(LocalDateTime.now().minusDays(89).format(FORMAT));

        assertThat(session.isExpire(90)).isFalse();
    }

    @Test
    @DisplayName("만료 일수는 인자로 받은 값을 따른다")
    void isExpire_respectsExpireDaysArgument() {
        SessionData session = new SessionData("sid-1");
        session.setCreateDate(LocalDateTime.now().minusDays(10).format(FORMAT));

        assertThat(session.isExpire(7)).isTrue();
        assertThat(session.isExpire(30)).isFalse();
    }

    @Test
    @DisplayName("createDate 형식이 잘못되면 만료로 처리한다")
    void isExpire_withMalformedCreateDate() {
        SessionData session = new SessionData("sid-1");
        session.setCreateDate("not-a-date");

        assertThat(session.isExpire(90)).isTrue();
    }

    @Test
    @DisplayName("createDate가 null이면 만료로 처리한다")
    void isExpire_withNullCreateDate() {
        SessionData session = new SessionData("sid-1");
        session.setCreateDate(null);

        assertThat(session.isExpire(90)).isTrue();
    }
}
