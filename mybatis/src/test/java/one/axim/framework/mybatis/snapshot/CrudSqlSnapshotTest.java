package one.axim.framework.mybatis.snapshot;

import one.axim.framework.core.data.XDirection;
import one.axim.framework.core.data.XOrder;
import one.axim.framework.core.data.XPagination;
import one.axim.framework.mybatis.model.XMapperParameter;
import one.axim.framework.mybatis.provider.CrudSqlProvider;
import one.axim.framework.mybatis.snapshot.SnapshotEntities.SnapChild;
import one.axim.framework.mybatis.snapshot.SnapshotEntities.SnapCode;
import one.axim.framework.mybatis.snapshot.SnapshotEntities.SnapOrderItem;
import one.axim.framework.mybatis.snapshot.SnapshotEntities.SnapOrderItemKey;
import one.axim.framework.mybatis.snapshot.SnapshotEntities.SnapUser;
import one.axim.framework.mybatis.utils.XPaginationSql;
import one.axim.framework.mybatis.dialect.XDialect;
import one.axim.framework.mybatis.dialect.XDialectHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL SQL 출력 회귀 고정 (v1.5.0 방언 도입 전 1.4.2 출력으로 작성).
 *
 * <p>{@code snapshot/mysql-1.4.2.txt} 는 1.4.2 코드(main 0127363)로 생성한 뒤 손대지 않는다.
 * 1.5.0 은 save() 버그 수정으로 자동증가 PK 엔티티의 upsert 에만 PK 줄이 추가됐고,
 * {@link #ALLOWED_DIFF_FROM_142} 외의 차이가 생기면 MySQL 소비자(cryptoments, TORQ)가 받는 SQL 이 바뀐 것이다.
 *
 * <p>스냅샷 재생성(의도된 변경일 때만): {@code ./gradlew :mybatis:test -Psnapshot.write=<디렉터리>}.
 * {@code mysql-1.4.2.txt} 는 재생성하지 않는다.
 */
class CrudSqlSnapshotTest {

    /** 1.4.2(main 0127363) 출력 — 역사 기록. 재생성하지 않는다. */
    static final String SNAPSHOT_142 = "/snapshot/mysql-1.4.2.txt";
    static final String SNAPSHOT = "/snapshot/mysql-1.5.0.txt";
    static final String PG_SNAPSHOT = "/snapshot/postgresql-1.5.0.txt";

    /**
     * 1.4.2 대비 MySQL 출력에서 바뀌어도 되는 유일한 부분 (v1.5.0 save() 버그 수정):
     * 자동증가 PK 엔티티의 upsert 에 PK 컬럼/값 줄이 추가된다. 그 외 섹션·줄은 1.4.2 와 같아야 한다.
     */
    static final Map<String, List<String>> ALLOWED_DIFF_FROM_142 = Map.of(
            "SnapUser.upsert", List.of("<if test=\"model.id != null\">id,</if>", "<if test=\"model.id != null\">#{model.id},</if>"),
            "SnapChild.upsert", List.of("<if test=\"model.id != null\">id,</if>", "<if test=\"model.id != null\">#{model.id},</if>"));

    /** 방언 미지정 상태의 전체 출력. */
    static String render() {
        CrudSqlProvider p = new CrudSqlProvider();
        StringBuilder out = new StringBuilder();

        List<Class<?>> entities = List.of(SnapUser.class, SnapOrderItem.class, SnapCode.class, SnapChild.class);
        for (Class<?> e : entities) {
            String n = e.getSimpleName();
            section(out, n + ".insert", p.insert(param(e, null)));
            section(out, n + ".insertAll", p.insertAll(param(e, null)));
            section(out, n + ".update", p.update(param(e, null)));
            section(out, n + ".selectiveUpdate", p.selectiveUpdate(param(e, null)));
            section(out, n + ".findAll", p.findAll(param(e, null)));
            section(out, n + ".count", p.count(param(e, null)));
            section(out, n + ".findOneBy", p.findOneBy(param(e, null)));
            section(out, n + ".upsert", p.upsert(param(e, null)));
            section(out, n + ".findById", p.findById(param(e, null)));
            section(out, n + ".delete[key]", p.delete(param(e, "KEY")));
        }

        // where-map 변형
        section(out, "SnapUser.findAll[where]", p.findAll(param(SnapUser.class, where("email", "status"))));
        section(out, "SnapUser.findWhere[where1]", p.findWhere(param(SnapUser.class, where("email"))));
        section(out, "SnapUser.findWhere[where2]", p.findWhere(param(SnapUser.class, where("status", "displayName"))));
        section(out, "SnapUser.count[where]", p.count(param(SnapUser.class, where("status"))));
        section(out, "SnapUser.findOneBy[where]", p.findOneBy(param(SnapUser.class, where("email"))));
        section(out, "SnapUser.update[where]", p.update(param(SnapUser.class, where("email"))));
        section(out, "SnapUser.selectiveUpdate[where]", p.selectiveUpdate(param(SnapUser.class, where("email", "status"))));
        section(out, "SnapUser.delete[where]", p.delete(param(SnapUser.class, where("email", "status"))));
        section(out, "SnapOrderItem.findWhere[where]", p.findWhere(param(SnapOrderItem.class, where("orderId", "memo"))));
        section(out, "SnapOrderItem.delete[where]", p.delete(param(SnapOrderItem.class, where("orderId"))));
        section(out, "SnapOrderItem.count[where]", p.count(param(SnapOrderItem.class, where("orderId", "itemNo"))));
        section(out, "SnapChild.findAll[where]", p.findAll(param(SnapChild.class, where("title"))));
        section(out, "SnapCode.selectiveUpdate[where]", p.selectiveUpdate(param(SnapCode.class, where("label"))));

        // 페이지네이션이 붙어도 provider 출력은 같아야 한다 (ORDER BY / LIMIT 은 인터셉터 몫)
        XMapperParameter paged = param(SnapUser.class, where("status"));
        paged.setPagination(pagination(2, 10, true));
        section(out, "SnapUser.findAll[where+pagination]", p.findAll(paged));

        // XPaginationSql.limit()
        BiFunction<Integer, Integer, XPagination> pg = (page, size) -> pagination(page, size, false);
        section(out, "limit[null]", XPaginationSql.limit(null));
        section(out, "limit[page1,size20]", XPaginationSql.limit(pg.apply(1, 20)));
        section(out, "limit[page3,size20]", XPaginationSql.limit(pg.apply(3, 20)));
        section(out, "limit[page2,size50000]", XPaginationSql.limit(pg.apply(2, 50000)));
        XPagination noLimit = pg.apply(0, 0);
        section(out, "limit[size0,offset0]", XPaginationSql.limit(noLimit));
        XPagination rawOffset = pg.apply(0, 15);
        rawOffset.setOffset(45);
        section(out, "limit[page0,size15,offset45]", XPaginationSql.limit(rawOffset));
        XPagination negative = pg.apply(0, -5);
        negative.setOffset(-3);
        section(out, "limit[size-5,offset-3]", XPaginationSql.limit(negative));
        section(out, "orderBy[two]", XPaginationSql.orderBy(pagination(1, 20, true)));

        // XMapperParameter.getRawQuery() — limit() 을 경유한다
        XMapperParameter raw = new XMapperParameter();
        raw.setRawQuery("SELECT * FROM t WHERE a = 1 AND () OR ()");
        raw.setPagination(pagination(4, 25, true));
        section(out, "rawQuery[pagination]", raw.getRawQuery());

        return out.toString();
    }

    @BeforeEach
    @AfterEach
    void unresolvedDialect() {
        // 통합 테스트가 같은 JVM 에서 먼저 돌며 전역 방언을 정해 둘 수 있다
        XDialectHolder.reset();
    }

    @Test
    @DisplayName("방언 미지정: MySQL 출력 고정")
    void mysqlOutputMatchesSnapshot() throws IOException {
        assertSnapshot(SNAPSHOT, render());
    }

    @Test
    @DisplayName("mysql 명시: 미지정과 같은 출력")
    void explicitMysqlMatchesSnapshot() throws IOException {
        XDialectHolder.set(XDialect.MYSQL);
        assertSnapshot(SNAPSHOT, render());
    }

    @Test
    @DisplayName("1.4.2 대비 MySQL 변경은 자동증가 PK upsert 의 PK 줄 추가뿐")
    void mysqlDiffFrom142IsOnlyUpsertAutoIncrementPk() throws IOException {
        Map<String, String> before = sections(readResource(SNAPSHOT_142));
        Map<String, String> now = sections(render());
        assertEquals(before.keySet(), now.keySet(), "섹션 목록이 같아야 한다");

        for (Map.Entry<String, String> e : before.entrySet()) {
            String name = e.getKey();
            String current = now.get(name);
            List<String> allowed = ALLOWED_DIFF_FROM_142.get(name);
            if (allowed == null) {
                assertEquals(e.getValue(), current, name + " 는 1.4.2 와 같아야 한다");
                continue;
            }
            List<String> added = new java.util.ArrayList<>(List.of(current.split("\n", -1)));
            for (String line : allowed) {
                assertTrue(added.remove(line), name + " 에 추가돼야 할 줄이 없다: " + line);
            }
            assertEquals(e.getValue(), String.join("\n", added), name + " 는 허용된 줄 외에 1.4.2 와 같아야 한다");
        }
    }

    @Test
    @DisplayName("postgresql: 출력 고정 (리뷰용 스냅샷)")
    void postgresqlOutput() throws IOException {
        XDialectHolder.set(XDialect.POSTGRESQL);
        assertSnapshot(PG_SNAPSHOT, render());
    }

    private static void assertSnapshot(String resource, String actual) throws IOException {
        String writeDir = System.getProperty("snapshot.write");
        if (writeDir != null && !writeDir.isBlank()) {
            Files.writeString(Path.of(writeDir, resource.substring(resource.lastIndexOf('/') + 1)),
                    actual, StandardCharsets.UTF_8);
        }

        assertEquals(readResource(resource), actual);
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream in = CrudSqlSnapshotTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "snapshot missing: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** "### name" 헤더로 나눈 섹션 (순서 유지). */
    private static Map<String, String> sections(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String chunk : text.split("(?m)^### ")) {
            if (chunk.isEmpty()) {
                continue;
            }
            int nl = chunk.indexOf('\n');
            out.put(chunk.substring(0, nl), chunk.substring(nl + 1));
        }
        return out;
    }

    // ── helpers ──

    private static void section(StringBuilder out, String name, String sql) {
        out.append("### ").append(name).append('\n').append(sql).append("\n\n");
    }

    private static XMapperParameter param(Class<?> entity, Object where) {
        XMapperParameter p = new XMapperParameter();
        p.setResultClass(entity);
        if ("KEY".equals(where)) {
            p.setWhere(entity == SnapOrderItem.class ? new SnapOrderItemKey() : 1L);
        } else if (where != null) {
            p.setWhere(where);
        }
        return p;
    }

    private static Map<String, Object> where(String... keys) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : keys) {
            m.put(k, "v");
        }
        return m;
    }

    private static XPagination pagination(int page, int size, boolean withOrders) {
        XPagination p = new XPagination();
        p.setPage(page);
        p.setSize(size);
        if (withOrders) {
            p.addOrder(new XOrder("createdAt", XDirection.DESC));
            p.addOrder(new XOrder("id", XDirection.ASC));
        }
        return p;
    }
}
