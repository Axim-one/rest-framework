package one.axim.framework.mybatis.repository;

import one.axim.framework.core.data.XPage;
import one.axim.framework.core.data.XPagination;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.SelectProvider;

public interface IXRepository<K, T> {

    K save(T model);

    K saveAll(List<T> entities);

    K insert(T model);

    int update(T model);

    int modify(T model);

    int deleteById(K key);

    int deleteWhere(Map<String, Object> where);

    int delete(K key);

    int remove(K key);

    T findOne(K key);

    List<T> findAll();

    XPage<T> findAll(XPagination pagination);

    boolean exists(K key);

    long count();

    long count(Map<String, Object> where);

    List<T> findWhere(Map<String, Object> where);

    XPage<T> findWhere(XPagination pagination, Map<String, Object> where);

    T findOneWhere(Map<String, Object> where);
}
