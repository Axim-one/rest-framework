package one.axim.framework.mybatis.it.repo;

import one.axim.framework.mybatis.annotation.XRepository;
import one.axim.framework.mybatis.it.entity.ItProduct;
import one.axim.framework.mybatis.repository.IXRepository;

@XRepository
public interface ItProductRepository extends IXRepository<Integer, ItProduct> {
}
