package one.axim.framework.mybatis.repository;

import one.axim.framework.core.data.XPage;
import one.axim.framework.core.data.XPagination;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.SelectProvider;

/**
 * Base repository interface providing built-in CRUD operations for entities.
 *
 * <p>Repository interfaces extend this with concrete key and entity types, and are annotated
 * with {@link one.axim.framework.mybatis.annotation.XRepository @XRepository}. The framework
 * generates a JDK Proxy implementation at startup that delegates to MyBatis.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @XRepository
 * public interface UserRepository extends IXRepository<Long, User> {
 *     // All methods below are available automatically.
 *     // Additional query-derivation methods can also be declared:
 *     User findByEmail(String email);
 *     List<User> findByStatus(String status);
 * }
 *
 * // Injection and usage:
 * @Autowired
 * private UserRepository userRepository;
 *
 * User user = new User();
 * user.setName("Alice");
 * userRepository.save(user);                       // upsert
 * User found = userRepository.findOne(user.getId());
 * }</pre>
 *
 * <h3>Key Method Differences</h3>
 * <ul>
 *   <li><b>save</b> vs <b>insert</b>: {@code save()} performs an upsert (INSERT ... ON DUPLICATE KEY UPDATE)
 *       when the PK is set; {@code insert()} always performs a plain INSERT.</li>
 *   <li><b>update</b> vs <b>modify</b>: {@code update()} sets ALL updatable columns (including nulls);
 *       {@code modify()} only sets non-null fields (selective update).</li>
 * </ul>
 *
 * @param <K> the primary key type (e.g., {@code Long}, {@code String})
 * @param <T> the entity type annotated with {@link one.axim.framework.mybatis.annotation.XEntity @XEntity}
 * @see one.axim.framework.mybatis.annotation.XRepository
 * @see one.axim.framework.mybatis.annotation.XEntity
 */
public interface IXRepository<K, T> {

    /**
     * Upsert: if the primary key is {@code null}, performs an INSERT (auto-generated ID is set on the entity);
     * if the primary key is present, performs INSERT ... ON DUPLICATE KEY UPDATE (atomic upsert).
     *
     * @param model the entity to save
     * @return the primary key of the saved entity
     */
    K save(T model);

    /**
     * Batch insert using INSERT IGNORE. Duplicate-key rows are silently skipped.
     *
     * @param entities the list of entities to insert
     * @return the primary key of the last inserted entity
     */
    K saveAll(List<T> entities);

    /**
     * Plain INSERT. The auto-generated ID (if any) is set on the entity after insertion.
     *
     * @param model the entity to insert
     * @return the auto-generated primary key
     */
    K insert(T model);

    /**
     * Full UPDATE — sets ALL updatable columns, including fields that are {@code null}.
     *
     * @param model the entity with the primary key set
     * @return the number of affected rows
     */
    int update(T model);

    /**
     * Selective UPDATE — only sets fields that are non-{@code null} (partial update).
     * Existing column values are preserved for fields not provided.
     *
     * @param model the entity with the primary key and desired fields set
     * @return the number of affected rows
     */
    int modify(T model);

    /**
     * Deletes the entity with the given primary key.
     *
     * @param key the primary key
     * @return the number of deleted rows
     */
    int deleteById(K key);

    /**
     * Deletes entities matching the given conditions.
     *
     * @param where a map of column-name to value conditions (AND-combined)
     * @return the number of deleted rows
     */
    int deleteWhere(Map<String, Object> where);

    /**
     * Alias for {@link #deleteById(Object)}.
     *
     * @param key the primary key
     * @return the number of deleted rows
     */
    int delete(K key);

    /**
     * Alias for {@link #deleteById(Object)}.
     *
     * @param key the primary key
     * @return the number of deleted rows
     */
    int remove(K key);

    /**
     * Finds a single entity by its primary key.
     *
     * @param key the primary key
     * @return the entity, or {@code null} if not found
     */
    T findOne(K key);

    /**
     * Returns all entities in the table.
     *
     * @return a list of all entities
     */
    List<T> findAll();

    /**
     * Returns a paginated result of all entities.
     *
     * @param pagination the pagination parameters (page, size, orders)
     * @return an {@link XPage} containing the result rows and total count
     */
    XPage<T> findAll(XPagination pagination);

    /**
     * Checks whether an entity with the given primary key exists.
     *
     * @param key the primary key
     * @return {@code true} if the entity exists
     */
    boolean exists(K key);

    /**
     * Returns the total number of entities in the table.
     *
     * @return the row count
     */
    long count();

    /**
     * Returns the number of entities matching the given conditions.
     *
     * @param where a map of column-name to value conditions (AND-combined)
     * @return the matching row count
     */
    long count(Map<String, Object> where);

    /**
     * Finds all entities matching the given conditions.
     *
     * @param where a map of column-name to value conditions (AND-combined)
     * @return a list of matching entities
     */
    List<T> findWhere(Map<String, Object> where);

    /**
     * Finds entities matching the given conditions with pagination.
     *
     * @param pagination the pagination parameters (page, size, orders)
     * @param where a map of column-name to value conditions (AND-combined)
     * @return an {@link XPage} containing the result rows and total count
     */
    XPage<T> findWhere(XPagination pagination, Map<String, Object> where);

    /**
     * Finds a single entity matching the given conditions.
     *
     * @param where a map of column-name to value conditions (AND-combined)
     * @return the matching entity, or {@code null} if not found
     */
    T findOneWhere(Map<String, Object> where);
}
