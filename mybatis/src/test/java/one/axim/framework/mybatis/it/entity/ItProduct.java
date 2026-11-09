package one.axim.framework.mybatis.it.entity;

import lombok.Data;
import one.axim.framework.mybatis.annotation.XColumn;
import one.axim.framework.mybatis.annotation.XEntity;

/** schema 지정 테이블 + Integer 자동증가 PK + Boolean 컬럼. */
@Data
@XEntity(value = "it_products", schema = "it_shop")
public class ItProduct {
    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Integer id;
    private String name;
    private Integer price;
    private Boolean active;

    public static ItProduct of(String name, int price, boolean active) {
        ItProduct p = new ItProduct();
        p.setName(name);
        p.setPrice(price);
        p.setActive(active);
        return p;
    }
}
