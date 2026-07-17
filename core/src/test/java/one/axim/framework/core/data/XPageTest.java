package one.axim.framework.core.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XPageTest {

    @Test
    @DisplayName("setPageRowsByObject: List를 받으면 그대로 설정한다")
    void setPageRowsByObject_withList() {
        XPage<String> page = new XPage<>();
        page.setPageRowsByObject(List.of("a", "b"));

        assertThat(page.getPageRows()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("setPageRowsByObject: null을 받으면 null로 설정한다")
    void setPageRowsByObject_withNull() {
        XPage<String> page = new XPage<>();
        page.setPageRowsByObject(null);

        assertThat(page.getPageRows()).isNull();
    }

    @Test
    @DisplayName("setPageRowsByObject: List가 아니면 IllegalArgumentException을 던진다")
    void setPageRowsByObject_withNonList() {
        XPage<String> page = new XPage<>();

        assertThatThrownBy(() -> page.setPageRowsByObject("not a list"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected List");
    }

    @Test
    @DisplayName("addPageRows: 기존 행에 이어붙인다")
    void addPageRows_appends() {
        XPage<String> page = new XPage<>();
        page.setPageRows(List.of("a"));
        page.addPageRows(List.of("b", "c"));

        assertThat(page.getPageRows()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("addPageRows: 기존 행이 없으면 그대로 설정한다")
    void addPageRows_whenEmpty() {
        XPage<String> page = new XPage<>();
        page.addPageRows(List.of("a"));

        assertThat(page.getPageRows()).containsExactly("a");
    }

    @Test
    @DisplayName("addPageRows: null이나 빈 리스트는 무시한다")
    void addPageRows_ignoresNullAndEmpty() {
        XPage<String> page = new XPage<>();
        page.setPageRows(List.of("a"));

        page.addPageRows(null);
        page.addPageRows(List.of());

        assertThat(page.getPageRows()).containsExactly("a");
    }

    @Test
    @DisplayName("getHasNext: 다음 페이지가 있으면 true다")
    void getHasNext_whenMorePagesExist() {
        XPage<String> page = new XPage<>();
        page.setPage(1);
        page.setSize(20);
        page.setTotalCount(50);

        assertThat(page.getHasNext()).isTrue();
    }

    @Test
    @DisplayName("getHasNext: 마지막 페이지면 false다")
    void getHasNext_onLastPage() {
        XPage<String> page = new XPage<>();
        page.setPage(3);
        page.setSize(20);
        page.setTotalCount(50);

        assertThat(page.getHasNext()).isFalse();
    }

    @Test
    @DisplayName("getHasNext: 전체가 한 페이지에 들어가면 false다")
    void getHasNext_whenSinglePage() {
        XPage<String> page = new XPage<>();
        page.setPage(1);
        page.setSize(20);
        page.setTotalCount(20);

        assertThat(page.getHasNext()).isFalse();
    }

    @Test
    @DisplayName("getHasNext: 결과가 없으면 false다")
    void getHasNext_whenEmpty() {
        XPage<String> page = new XPage<>();
        page.setPage(1);
        page.setSize(20);
        page.setTotalCount(0);

        assertThat(page.getHasNext()).isFalse();
    }

    @Test
    @DisplayName("getHasNext: 필드가 설정되지 않았으면 false다")
    void getHasNext_withUnsetFields() {
        assertThat(new XPage<String>().getHasNext()).isFalse();

        XPage<String> noTotal = new XPage<>();
        noTotal.setPage(1);
        noTotal.setSize(20);
        assertThat(noTotal.getHasNext()).isFalse();

        XPage<String> zeroSize = new XPage<>();
        zeroSize.setPage(1);
        zeroSize.setSize(0);
        zeroSize.setTotalCount(50);
        assertThat(zeroSize.getHasNext()).isFalse();
    }
}
