package one.axim.framework.demo.user;

import one.axim.framework.mybatis.annotation.XColumn;
import one.axim.framework.mybatis.annotation.XEntity;
import lombok.Data;

@Data
@XEntity("user_profile")
public class UserProfile {

    // PK = FK (users.id) — isAutoIncrement=false → triggers the findOne swap path.
    @XColumn(isPrimaryKey = true)
    private Integer userId;

    private String nickname;

    private String bio;
}
