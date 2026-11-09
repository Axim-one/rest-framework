package one.axim.framework.mybatis.snapshot;

import lombok.Data;
import lombok.EqualsAndHashCode;
import one.axim.framework.mybatis.annotation.XColumn;
import one.axim.framework.mybatis.annotation.XDefaultValue;
import one.axim.framework.mybatis.annotation.XEntity;
import one.axim.framework.mybatis.annotation.XIgnoreColumn;

/**
 * SQL 스냅샷 테스트 전용 엔티티 모음. DB 테이블과 무관하다.
 * 1.4.2 의 {@code CrudSqlProvider} 출력을 고정하기 위해 프레임워크가 다루는
 * 모든 매핑 변형(자동증가 PK, 복합 PK, schema, @XDefaultValue 4종, insert/update=false,
 * 상속, @XIgnoreColumn)을 한 번씩 포함한다.
 */
final class SnapshotEntities {

    private SnapshotEntities() {
    }

    @Data
    @XEntity("users")
    public static class SnapUser {
        @XColumn(isPrimaryKey = true, isAutoIncrement = true)
        private Long id;
        private String email;
        private String displayName;
        @XDefaultValue(value = "ACTIVE", isDBDefaultUsed = false)
        private String status;
        @XDefaultValue(value = "NOW()", isDBValue = true, isDBDefaultUsed = false)
        private java.time.LocalDateTime createdAt;
        @XDefaultValue(updateValue = "NOW()", isDBValue = true)
        private java.time.LocalDateTime updatedAt;
        @XDefaultValue
        private Integer loginCount;
        @XIgnoreColumn
        private String transientNote;
    }

    @Data
    @XEntity(value = "order_items", schema = "shop")
    public static class SnapOrderItem {
        @XColumn(isPrimaryKey = true)
        private Long orderId;
        @XColumn(isPrimaryKey = true)
        private Integer itemNo;
        private Integer qty;
        @XColumn(update = false)
        private String createdBy;
        @XColumn(value = "memo_text", insert = false)
        private String memo;
    }

    @Data
    public static class SnapOrderItemKey {
        private Long orderId;
        private Integer itemNo;
    }

    @Data
    @XEntity("codes")
    public static class SnapCode {
        @XColumn(isPrimaryKey = true)
        private String code;
        private String label;
    }

    @Data
    public static class SnapBase {
        @XColumn(isPrimaryKey = true, isAutoIncrement = true)
        private Integer id;
        @XDefaultValue(value = "CURRENT_TIMESTAMP", updateValue = "CURRENT_TIMESTAMP", isDBValue = true, isDBDefaultUsed = false)
        private java.time.LocalDateTime modifiedAt;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @XEntity(value = "child_rows", schema = "app")
    public static class SnapChild extends SnapBase {
        private String title;
        @XDefaultValue(value = "it's", updateValue = "it's", isDBDefaultUsed = false)
        private String quoted;
    }
}
