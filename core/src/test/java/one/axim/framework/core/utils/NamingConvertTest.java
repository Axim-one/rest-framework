package one.axim.framework.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamingConvertTest {

    @Test
    @DisplayName("toCamelCase: 단일 언더스코어는 다음 글자를 대문자로 만든다")
    void toCamelCase_singleUnderscore() {
        assertThat(NamingConvert.toCamelCase("user_name")).isEqualTo("userName");
        assertThat(NamingConvert.toCamelCase("created_at_date")).isEqualTo("createdAtDate");
    }

    @Test
    @DisplayName("toCamelCase: 연속 언더스코어를 하나로 취급한다")
    void toCamelCase_consecutiveUnderscores() {
        assertThat(NamingConvert.toCamelCase("a__b")).isEqualTo("aB");
        assertThat(NamingConvert.toCamelCase("a___b")).isEqualTo("aB");
    }

    @Test
    @DisplayName("toCamelCase: 선행/후행 언더스코어 동작을 보존한다")
    void toCamelCase_leadingAndTrailingUnderscore() {
        assertThat(NamingConvert.toCamelCase("_abc")).isEqualTo("Abc");
        assertThat(NamingConvert.toCamelCase("abc_")).isEqualTo("abc");
    }

    @Test
    @DisplayName("toCamelCase: 언더스코어가 없으면 그대로 반환한다")
    void toCamelCase_noUnderscore() {
        assertThat(NamingConvert.toCamelCase("username")).isEqualTo("username");
    }

    @Test
    @DisplayName("toCamelCase: null과 빈 문자열은 그대로 반환한다")
    void toCamelCase_nullAndEmpty() {
        assertThat(NamingConvert.toCamelCase(null)).isNull();
        assertThat(NamingConvert.toCamelCase("")).isEmpty();
    }

    @Test
    @DisplayName("toUnderScoreName: 카멜케이스를 스네이크케이스로 바꾼다")
    void toUnderScoreName_basic() {
        assertThat(NamingConvert.toUnderScoreName("userName")).isEqualTo("user_name");
        assertThat(NamingConvert.toUnderScoreName("createdAtDate")).isEqualTo("created_at_date");
    }

    @Test
    @DisplayName("toUnderScoreName: 연속 대문자 약어를 처리한다")
    void toUnderScoreName_consecutiveUpperCase() {
        assertThat(NamingConvert.toUnderScoreName("XMLHttpRequest")).isEqualTo("xml_http_request");
    }

    @Test
    @DisplayName("toUnderScoreName: null과 빈 문자열은 그대로 반환한다")
    void toUnderScoreName_nullAndEmpty() {
        assertThat(NamingConvert.toUnderScoreName(null)).isNull();
        assertThat(NamingConvert.toUnderScoreName("")).isEmpty();
    }

    @Test
    @DisplayName("toCamelCaseByClassName: 스네이크케이스를 파스칼케이스로 바꾼다")
    void toCamelCaseByClassName_basic() {
        assertThat(NamingConvert.toCamelCaseByClassName("user_account")).isEqualTo("UserAccount");
    }

    @Test
    @DisplayName("toCamelCaseByClassName: 빈 파트를 건너뛴다")
    void toCamelCaseByClassName_emptyParts() {
        assertThat(NamingConvert.toCamelCaseByClassName("user__account")).isEqualTo("UserAccount");
    }
}
