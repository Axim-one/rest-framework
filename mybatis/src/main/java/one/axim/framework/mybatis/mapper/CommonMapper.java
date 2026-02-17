package one.axim.framework.mybatis.mapper;

import one.axim.framework.mybatis.model.XMapperParameter;
import one.axim.framework.mybatis.provider.CrudSqlProvider;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Primary
@Mapper
public interface CommonMapper {

    @InsertProvider(type = CrudSqlProvider.class, method = "insert")
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "lastInsertedId", before = false, resultType = Long.class)
    Long insertAndSelectKey(XMapperParameter model);

    @InsertProvider(type = CrudSqlProvider.class, method = "insert")
    Long insert(XMapperParameter model);

    @InsertProvider(type = CrudSqlProvider.class, method = "upsert")
    int upsert(XMapperParameter model);

    @InsertProvider(type = CrudSqlProvider.class, method = "insertAll")
    Long insertAll(XMapperParameter model);

    @DeleteProvider(type = CrudSqlProvider.class, method = "delete")
    int delete(XMapperParameter model);

    @UpdateProvider(type = CrudSqlProvider.class, method = "update")
    int update(XMapperParameter model);

    @UpdateProvider(type = CrudSqlProvider.class, method = "selectiveUpdate")
    int selectiveUpdate(XMapperParameter model);

    @SelectProvider(type = CrudSqlProvider.class, method = "findById")
    <T> T findById(XMapperParameter model);

    @SelectProvider(type = CrudSqlProvider.class, method = "findOneBy")
    <T> T findOneBy(XMapperParameter model);

    @SelectProvider(type = CrudSqlProvider.class, method = "count")
    long count(XMapperParameter model);

    @SelectProvider(type = CrudSqlProvider.class, method = "findAll")
    <T> List<T> findAll(XMapperParameter model);

    @SelectProvider(type = CrudSqlProvider.class, method = "findWhere")
    <T> List<T> findWhere(XMapperParameter model);
}