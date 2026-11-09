package one.axim.framework.mybatis.it.entity;

import lombok.Data;
import one.axim.framework.mybatis.annotation.XColumn;
import one.axim.framework.mybatis.annotation.XDefaultValue;
import one.axim.framework.mybatis.annotation.XEntity;

import java.time.LocalDateTime;

@Data
@XEntity("it_users")
public class ItUser {
    @XColumn(isPrimaryKey = true, isAutoIncrement = true)
    private Long id;
    private String email;
    private String name;
    @XDefaultValue
    private String status;
    @XColumn(update = false)
    @XDefaultValue(value = "CURRENT_TIMESTAMP", isDBValue = true, isDBDefaultUsed = false)
    private LocalDateTime createdAt;

    public static ItUser of(String email, String name) {
        ItUser u = new ItUser();
        u.setEmail(email);
        u.setName(name);
        return u;
    }
}
