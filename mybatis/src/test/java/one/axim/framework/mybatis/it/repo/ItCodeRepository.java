package one.axim.framework.mybatis.it.repo;

import one.axim.framework.mybatis.annotation.XRepository;
import one.axim.framework.mybatis.it.entity.ItCode;
import one.axim.framework.mybatis.repository.IXRepository;

@XRepository
public interface ItCodeRepository extends IXRepository<String, ItCode> {
}
