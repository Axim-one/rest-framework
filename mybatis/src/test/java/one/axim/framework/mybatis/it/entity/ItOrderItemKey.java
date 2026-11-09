package one.axim.framework.mybatis.it.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItOrderItemKey {
    private Long orderId;
    private Integer itemNo;
}
