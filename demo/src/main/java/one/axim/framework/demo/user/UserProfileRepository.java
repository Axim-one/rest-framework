package one.axim.framework.demo.user;

import one.axim.framework.mybatis.annotation.XRepository;
import one.axim.framework.mybatis.repository.IXRepository;

@XRepository
public interface UserProfileRepository extends IXRepository<Integer, UserProfile> {
}
