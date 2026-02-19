package one.axim.framework.mybatis.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as a repository that is auto-implemented by the framework via JDK Proxy.
 *
 * <p>Repository interfaces must extend {@link one.axim.framework.mybatis.repository.IXRepository IXRepository}
 * to inherit built-in CRUD methods. Custom query methods can be declared using
 * <strong>Query Derivation</strong> — the framework parses the method name to generate SQL automatically.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @XRepository
 * public interface UserRepository extends IXRepository<Long, User> {
 *     // Built-in: save, insert, update, modify, deleteById, findOne, findAll, exists, count, ...
 *
 *     // Query Derivation — method name is parsed into SQL:
 *     User findByEmail(String email);
 *     List<User> findByStatus(String status);
 *     long countByStatus(String status);
 *     boolean existsByEmail(String email);
 *     int deleteByStatus(String status);
 * }
 * }</pre>
 *
 * <h3>Supported Query Derivation Prefixes</h3>
 * <ul>
 *   <li>{@code findBy / findAllBy} — SELECT with WHERE conditions</li>
 *   <li>{@code countBy} — SELECT COUNT(*) with WHERE conditions</li>
 *   <li>{@code existsBy} — existence check (COUNT > 0)</li>
 *   <li>{@code deleteBy} — DELETE with WHERE conditions</li>
 * </ul>
 * <p>Conditions can be combined with {@code And} (e.g., {@code findByNameAndStatus}).</p>
 *
 * @see one.axim.framework.mybatis.repository.IXRepository
 * @see XRepositoryScan
 * @see XEntity
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface XRepository {

}
