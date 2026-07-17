package one.axim.framework.core.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XPaginationTest {

    @Test
    @DisplayName("기본값은 page=1, size=20이다")
    void defaults() {
        XPagination pagination = new XPagination();

        assertThat(pagination.getPage()).isEqualTo(XPagination.DEFAULT_PAGE);
        assertThat(pagination.getSize()).isEqualTo(XPagination.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("첫 페이지의 offset은 0이다")
    void getOffset_firstPage() {
        XPagination pagination = new XPagination();

        assertThat(pagination.getOffset()).isZero();
    }

    @Test
    @DisplayName("offset은 (page - 1) * size로 계산된다")
    void getOffset_calculatedFromPageAndSize() {
        XPagination pagination = new XPagination();
        pagination.setPage(3);
        pagination.setSize(20);

        assertThat(pagination.getOffset()).isEqualTo(40);
    }

    @Test
    @DisplayName("page/size가 유효하면 setOffset으로 넣은 값은 무시된다")
    void getOffset_pageAndSizeTakePrecedenceOverSetOffset() {
        XPagination pagination = new XPagination();
        pagination.setOffset(999);

        // page=1, size=20 (기본값)이 유효하므로 계산값 0이 우선한다
        assertThat(pagination.getOffset()).isZero();
    }

    @Test
    @DisplayName("size가 0이면 setOffset으로 넣은 값이 쓰인다")
    void getOffset_fallsBackToOffsetWhenSizeIsZero() {
        XPagination pagination = new XPagination();
        pagination.setSize(0);
        pagination.setOffset(999);

        assertThat(pagination.getOffset()).isEqualTo(999);
    }

    @Test
    @DisplayName("page가 0이면 setOffset으로 넣은 값이 쓰인다")
    void getOffset_fallsBackToOffsetWhenPageIsZero() {
        XPagination pagination = new XPagination();
        pagination.setPage(0);
        pagination.setOffset(999);

        assertThat(pagination.getOffset()).isEqualTo(999);
    }

    @Test
    @DisplayName("hasLimit: size와 offset이 모두 0일 때만 false다")
    void hasLimit() {
        XPagination defaults = new XPagination();
        assertThat(defaults.hasLimit()).isTrue();

        XPagination noLimit = new XPagination();
        noLimit.setSize(0);
        noLimit.setOffset(0);
        assertThat(noLimit.hasLimit()).isFalse();

        XPagination offsetOnly = new XPagination();
        offsetOnly.setSize(0);
        offsetOnly.setOffset(10);
        assertThat(offsetOnly.hasLimit()).isTrue();
    }

    @Test
    @DisplayName("addOrder: null을 거부한다")
    void addOrder_rejectsNull() {
        XPagination pagination = new XPagination();

        assertThatThrownBy(() -> pagination.addOrder(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getOrders: 수정 불가능한 리스트를 반환한다")
    void getOrders_isUnmodifiable() {
        XPagination pagination = new XPagination();
        pagination.addOrder(new XOrder("createdAt", XDirection.DESC));

        assertThat(pagination.hasOrder()).isTrue();
        assertThatThrownBy(() -> pagination.getOrders().add(new XOrder("id", XDirection.ASC)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
