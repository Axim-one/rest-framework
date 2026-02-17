package one.axim.framework.demo.user;

import one.axim.framework.mybatis.annotation.XColumn;
import one.axim.framework.mybatis.annotation.XEntity;
import lombok.Data;

@Data
@XEntity("users")
public class User {

    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Integer id;

    @XColumn
    private String name;

    @XColumn
    private String email;
}
