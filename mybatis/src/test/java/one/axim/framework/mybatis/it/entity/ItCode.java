package one.axim.framework.mybatis.it.entity;

import lombok.Data;
import one.axim.framework.mybatis.annotation.XColumn;
import one.axim.framework.mybatis.annotation.XEntity;

/** 자동증가가 아닌 문자열 PK — save() 가 upsert 경로를 탄다. */
@Data
@XEntity("it_codes")
public class ItCode {
    @XColumn(isPrimaryKey = true)
    private String code;
    private String label;
    private Integer sortOrder;

    public static ItCode of(String code, String label, Integer sortOrder) {
        ItCode c = new ItCode();
        c.setCode(code);
        c.setLabel(label);
        c.setSortOrder(sortOrder);
        return c;
    }
}
