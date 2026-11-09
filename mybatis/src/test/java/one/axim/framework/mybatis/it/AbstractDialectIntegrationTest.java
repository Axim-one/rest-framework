package one.axim.framework.mybatis.it;

import one.axim.framework.core.data.XDirection;
import one.axim.framework.core.data.XOrder;
import one.axim.framework.core.data.XPage;
import one.axim.framework.core.data.XPagination;
import one.axim.framework.mybatis.dialect.XDialect;
import one.axim.framework.mybatis.dialect.XDialectHolder;
import one.axim.framework.mybatis.it.entity.ItCode;
import one.axim.framework.mybatis.it.entity.ItOrderItem;
import one.axim.framework.mybatis.it.entity.ItOrderItemKey;
import one.axim.framework.mybatis.it.entity.ItProduct;
import one.axim.framework.mybatis.it.entity.ItUser;
import one.axim.framework.mybatis.it.mapper.ItUserMapper;
import one.axim.framework.mybatis.it.repo.ItCodeRepository;
import one.axim.framework.mybatis.it.repo.ItOrderItemRepository;
import one.axim.framework.mybatis.it.repo.ItProductRepository;
import one.axim.framework.mybatis.it.repo.ItUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 같은 스위트를 MySQL·PostgreSQL 컨테이너에서 돌린다 (v1.5.0).
 *
 * <p>방언은 JVM 전역이므로 DB 별 컨텍스트가 겹치지 않게 클래스마다 컨텍스트를 닫는다.
 */
@SpringBootTest(classes = ItTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
abstract class AbstractDialectIntegrationTest {

    @Autowired
    ItUserRepository users;
    @Autowired
    ItCodeRepository codes;
    @Autowired
    ItOrderItemRepository orderItems;
    @Autowired
    ItProductRepository products;
    @Autowired
    ItUserMapper userMapper;

    abstract XDialect expectedDialect();

    @Test
    @DisplayName("방언이 기대값으로 정해진다")
    void dialectResolved() {
        assertTrue(XDialectHolder.isResolved());
        assertEquals(expectedDialect(), XDialectHolder.current());
    }

    // ── insert / 자동 ID ──

    @Test
    @DisplayName("insert: 자동증가 ID 를 회수해 반환하고 모델에도 넣는다")
    void insertReturnsGeneratedId() {
        ItUser a = ItUser.of("a@x.io", "alice");
        Long idA = users.insert(a);
        assertNotNull(idA);
        assertEquals(idA, a.getId());

        ItUser b = ItUser.of("b@x.io", "bob");
        Long idB = users.insert(b);
        assertEquals(idA + 1, idB);

        ItUser found = users.findOne(idA);
        assertEquals("a@x.io", found.getEmail());
        assertEquals("ACTIVE", found.getStatus(), "DB DEFAULT 사용(@XDefaultValue)");
        assertNotNull(found.getCreatedAt(), "@XDefaultValue(CURRENT_TIMESTAMP, isDBValue)");
    }

    @Test
    @DisplayName("schema 지정 테이블: Integer 자동증가 ID · Boolean · CRUD")
    void schemaQualifiedTable() {
        ItProduct p = ItProduct.of("pen", 1200, true);
        Integer id = products.insert(p);
        assertNotNull(id);
        assertEquals(id, p.getId());

        ItProduct found = products.findOne(id);
        assertEquals("pen", found.getName());
        assertEquals(Boolean.TRUE, found.getActive());

        found.setPrice(1500);
        found.setActive(false);
        assertEquals(1, products.update(found));
        assertEquals(1500, products.findOne(id).getPrice());
        assertEquals(Boolean.FALSE, products.findOne(id).getActive());

        assertEquals(1, products.count());
        assertEquals(1, products.findAll().size());
        assertEquals(1, products.delete(id));
        assertNull(products.findOne(id));
    }

    // ── save ──

    @Test
    @DisplayName("save: PK 없음 → insert, PK 있음 → upsert(갱신)")
    void saveInsertThenUpdate() {
        ItUser u = ItUser.of("s@x.io", "sam");
        Long id = users.save(u);
        assertNotNull(id);
        assertEquals(id, u.getId());

        ItUser loaded = users.findOne(id);
        loaded.setName("samuel");
        assertEquals(id, users.save(loaded));

        assertEquals("samuel", users.findOne(id).getName());
        assertEquals(1, users.count());
    }

    @Test
    @DisplayName("save: 지정 PK 신규 → insert, 같은 PK 다시 → 갱신")
    void saveAssignedPk() {
        assertEquals("A", codes.save(ItCode.of("A", "alpha", 1)));
        assertEquals("alpha", codes.findOne("A").getLabel());

        assertEquals("A", codes.save(ItCode.of("A", "ALPHA", 9)));
        ItCode a = codes.findOne("A");
        assertEquals("ALPHA", a.getLabel());
        assertEquals(9, a.getSortOrder());
        assertEquals(1, codes.count());
    }

    @Test
    @DisplayName("save: 복합 PK upsert · findOne/delete(키 객체)")
    void saveCompositePk() {
        ItOrderItemKey key = orderItems.save(ItOrderItem.of(10L, 1, 3, "first"));
        assertEquals(new ItOrderItemKey(10L, 1), key);
        orderItems.save(ItOrderItem.of(10L, 2, 1, "second"));
        orderItems.save(ItOrderItem.of(10L, 1, 5, "first-updated"));

        ItOrderItem found = orderItems.findOne(new ItOrderItemKey(10L, 1));
        assertEquals(5, found.getQty());
        assertEquals("first-updated", found.getMemo());
        assertEquals(2, orderItems.count());
        assertTrue(orderItems.exists(new ItOrderItemKey(10L, 2)));

        assertEquals(1, orderItems.delete(new ItOrderItemKey(10L, 2)));
        assertFalse(orderItems.exists(new ItOrderItemKey(10L, 2)));
    }

    // ── saveAll ──

    @Test
    @DisplayName("saveAll: 중복 PK 는 조용히 건너뛴다")
    void saveAllIgnoresDuplicatePk() {
        codes.save(ItCode.of("A", "alpha", 1));
        codes.save(ItCode.of("B", "beta", 2));

        int affected = codes.saveAll(List.of(ItCode.of("B", "beta-dup", 99), ItCode.of("C", "gamma", 3)));
        assertEquals(1, affected);
        assertEquals("beta", codes.findOne("B").getLabel(), "중복 행은 덮어쓰지 않는다");
        assertEquals("gamma", codes.findOne("C").getLabel());
        assertEquals(3, codes.count());
    }

    @Test
    @DisplayName("saveAll: 자동증가 테이블에서 unique 중복도 건너뛴다")
    void saveAllIgnoresDuplicateUnique() {
        users.insert(ItUser.of("dup@x.io", "first"));
        int affected = users.saveAll(List.of(ItUser.of("dup@x.io", "second"), ItUser.of("new@x.io", "new")));
        assertEquals(1, affected);
        assertEquals("first", users.findByEmail("dup@x.io").getName());
        assertEquals(2, users.count());
    }

    // ── 페이지네이션 ──

    @Test
    @DisplayName("findAll(pagination): 페이지 1·2·3 + total count")
    void findAllPaginated() {
        long[] ids = seedFiveUsers();

        XPage<ItUser> p1 = users.findAll(page(1, 2, "id", XDirection.ASC));
        assertEquals(5, p1.getTotalCount());
        assertEquals(List.of(ids[0], ids[1]), idsOf(p1));

        XPage<ItUser> p2 = users.findAll(page(2, 2, "id", XDirection.ASC));
        assertEquals(5, p2.getTotalCount());
        assertEquals(List.of(ids[2], ids[3]), idsOf(p2));

        XPage<ItUser> p3 = users.findAll(page(3, 2, "id", XDirection.ASC));
        assertEquals(List.of(ids[4]), idsOf(p3));

        XPage<ItUser> desc = users.findAll(page(1, 3, "id", XDirection.DESC));
        assertEquals(List.of(ids[4], ids[3], ids[2]), idsOf(desc));
    }

    @Test
    @DisplayName("findWhere(pagination, where): 조건 + 페이지")
    void findWherePaginated() {
        long[] ids = seedFiveUsers();
        XPage<ItUser> page = users.findWhere(page(2, 1, "id", XDirection.ASC), Map.of("status", "BLOCKED"));
        assertEquals(2, page.getTotalCount());
        assertEquals(List.of(ids[3]), idsOf(page));
    }

    @Test
    @DisplayName("커스텀 @Select + XPage: 페이지 1·2 + total count")
    void customSelectPaginated() {
        long[] ids = seedFiveUsers();
        XPage<ItUser> p1 = userMapper.searchByName(page(1, 2, "id", XDirection.ASC), "user");
        assertEquals(5, p1.getTotalCount());
        assertEquals(List.of(ids[0], ids[1]), idsOf(p1));

        XPage<ItUser> p2 = userMapper.searchByName(page(2, 2, "id", XDirection.ASC), "user");
        assertEquals(List.of(ids[2], ids[3]), idsOf(p2));
        assertEquals("u3@x.io", p2.getPageRows().get(0).getEmail());
    }

    @Test
    @DisplayName("커스텀 @Select 의 ORDER BY: 명시 정렬 없으면 존중, 있으면 대체")
    void customSelectOrderBy() {
        long[] ids = seedFiveUsers();
        XPagination noOrder = new XPagination();
        noOrder.setPage(1);
        noOrder.setSize(10);
        XPage<ItUser> callerOrder = userMapper.findByStatusOrdered(noOrder, "ACTIVE");
        assertEquals(3, callerOrder.getTotalCount());
        assertEquals(List.of(ids[4], ids[2], ids[0]), idsOf(callerOrder), "email DESC");

        XPage<ItUser> explicit = userMapper.findByStatusOrdered(page(1, 2, "id", XDirection.ASC), "ACTIVE");
        assertEquals(List.of(ids[0], ids[2]), idsOf(explicit));
    }

    // ── 파생 쿼리 ──

    @Test
    @DisplayName("findBy* / findAllBy* / countBy* / existsBy* / deleteBy*")
    void derivedQueries() {
        seedFiveUsers();
        assertEquals("user2", users.findByEmail("u2@x.io").getName());
        assertNull(users.findByEmail("none@x.io"));
        assertEquals("u4@x.io", users.findByStatusAndName("BLOCKED", "user4").getEmail());

        assertEquals(2, users.findAllByStatus("BLOCKED").size());
        assertEquals(3, users.countByStatus("ACTIVE"));
        assertTrue(users.existsByEmail("u1@x.io"));
        assertFalse(users.existsByEmail("none@x.io"));

        assertEquals(1, users.deleteByEmail("u1@x.io"));
        assertFalse(users.existsByEmail("u1@x.io"));
    }

    @Test
    @DisplayName("findWhere / findOneWhere / count(where) / deleteWhere")
    void whereMapQueries() {
        seedFiveUsers();
        assertEquals(2, users.findWhere(Map.of("status", "BLOCKED")).size());
        assertEquals("user5", users.findOneWhere(Map.of("email", "u5@x.io")).getName());
        assertEquals(3, users.count(Map.of("status", "ACTIVE")));
        assertEquals(2, users.deleteWhere(Map.of("status", "BLOCKED")));
        assertEquals(3, users.count());
    }

    // ── update / delete ──

    @Test
    @DisplayName("modify(selectiveUpdate): null 필드는 건드리지 않는다")
    void selectiveUpdate() {
        ItUser u = ItUser.of("m@x.io", "mike");
        Long id = users.insert(u);

        ItUser patch = new ItUser();
        patch.setId(id);
        patch.setName("michael");
        assertEquals(1, users.modify(patch));

        ItUser found = users.findOne(id);
        assertEquals("michael", found.getName());
        assertEquals("m@x.io", found.getEmail());
        assertEquals("ACTIVE", found.getStatus());
    }

    @Test
    @DisplayName("update: 전체 컬럼 갱신")
    void fullUpdate() {
        Long id = users.insert(ItUser.of("f@x.io", "fred"));
        ItUser u = users.findOne(id);
        u.setEmail("fred@x.io");
        u.setStatus("BLOCKED");
        assertEquals(1, users.update(u));
        ItUser found = users.findOne(id);
        assertEquals("fred@x.io", found.getEmail());
        assertEquals("BLOCKED", found.getStatus());
    }

    @Test
    @DisplayName("delete / deleteById / exists")
    void deleteByKey() {
        Long id = users.insert(ItUser.of("d@x.io", "dan"));
        assertTrue(users.exists(id));
        assertEquals(1, users.delete(id));
        assertFalse(users.exists(id));
        assertEquals(0, users.deleteById(id));
    }

    // ── helpers ──

    /** u1..u5, status: ACTIVE, BLOCKED, ACTIVE, BLOCKED, ACTIVE. 반환 = id 순서. */
    long[] seedFiveUsers() {
        long[] ids = new long[5];
        for (int i = 1; i <= 5; i++) {
            ItUser u = ItUser.of("u" + i + "@x.io", "user" + i);
            ids[i - 1] = users.insert(u);
            if (i % 2 == 0) {
                ItUser patch = new ItUser();
                patch.setId(ids[i - 1]);
                patch.setStatus("BLOCKED");
                users.modify(patch);
            }
        }
        return ids;
    }

    static XPagination page(int page, int size, String column, XDirection direction) {
        XPagination p = new XPagination();
        p.setPage(page);
        p.setSize(size);
        p.addOrder(new XOrder(column, direction));
        return p;
    }

    static List<Long> idsOf(XPage<ItUser> page) {
        return page.getPageRows().stream().map(ItUser::getId).toList();
    }
}
