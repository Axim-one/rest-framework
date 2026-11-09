package one.axim.framework.mybatis.it.mapper;

import one.axim.framework.core.data.XPage;
import one.axim.framework.core.data.XPagination;
import one.axim.framework.mybatis.it.entity.ItUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ItUserMapper {

    /** CONCAT 은 MySQL·PostgreSQL 공통. */
    @Select("SELECT * FROM it_users WHERE name LIKE CONCAT('%', #{keyword}, '%')")
    XPage<ItUser> searchByName(XPagination pagination, @Param("keyword") String keyword);

    /** 호출자 ORDER BY — 명시 정렬이 있으면 인터셉터가 대체한다. */
    @Select("SELECT * FROM it_users WHERE status = #{status} ORDER BY email DESC")
    XPage<ItUser> findByStatusOrdered(XPagination pagination, @Param("status") String status);
}
