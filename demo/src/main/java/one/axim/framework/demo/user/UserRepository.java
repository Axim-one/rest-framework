package one.axim.framework.demo.user;

import java.util.List;

import one.axim.framework.mybatis.annotation.XRepository;
import one.axim.framework.mybatis.repository.IXRepository;


@XRepository
public interface UserRepository extends IXRepository<Integer, User> {
    User findByEmail(String email);

    List<User> findByName(String name);

    boolean existsByEmail(String email);
    boolean existsByName(String name);
}