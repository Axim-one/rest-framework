package one.axim.framework.demo.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import one.axim.framework.core.data.XPage;
import one.axim.framework.core.data.XPagination;


@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE email LIKE CONCAT('%', #{keyword}, '%')")
    XPage<User> searchUser(XPagination pagination, @Param("keyword") String keyword, Class<?> cls);
}
