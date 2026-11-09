package one.axim.framework.mybatis.it.repo;

import one.axim.framework.mybatis.annotation.XRepository;
import one.axim.framework.mybatis.it.entity.ItOrderItem;
import one.axim.framework.mybatis.it.entity.ItOrderItemKey;
import one.axim.framework.mybatis.repository.IXRepository;

@XRepository
public interface ItOrderItemRepository extends IXRepository<ItOrderItemKey, ItOrderItem> {
}
