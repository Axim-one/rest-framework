package one.axim.framework.mybatis.it.repo;

import one.axim.framework.mybatis.annotation.XRepository;
import one.axim.framework.mybatis.it.entity.ItUser;
import one.axim.framework.mybatis.repository.IXRepository;

import java.util.List;

@XRepository
public interface ItUserRepository extends IXRepository<Long, ItUser> {
    ItUser findByEmail(String email);

    List<ItUser> findAllByStatus(String status);

    ItUser findByStatusAndName(String status, String name);

    long countByStatus(String status);

    boolean existsByEmail(String email);

    int deleteByEmail(String email);
}
