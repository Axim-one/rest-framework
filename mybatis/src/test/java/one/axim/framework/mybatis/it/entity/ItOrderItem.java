package one.axim.framework.mybatis.it.entity;

import lombok.Data;
import one.axim.framework.mybatis.annotation.XColumn;
import one.axim.framework.mybatis.annotation.XEntity;

/** 복합 PK. */
@Data
@XEntity("it_order_items")
public class ItOrderItem {
    @XColumn(isPrimaryKey = true)
    private Long orderId;
    @XColumn(isPrimaryKey = true)
    private Integer itemNo;
    private Integer qty;
    private String memo;

    public static ItOrderItem of(long orderId, int itemNo, int qty, String memo) {
        ItOrderItem i = new ItOrderItem();
        i.setOrderId(orderId);
        i.setItemNo(itemNo);
        i.setQty(qty);
        i.setMemo(memo);
        return i;
    }
}
