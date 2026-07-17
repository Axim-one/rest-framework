# TODO High Priority 일괄 처리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `TODO.md` High Priority 전체를 처리한다 — `XRestEnvironment` 정적 싱글톤 제거, Security 4건, `saveAll` 반환 타입 수정, `XResultInterceptor.setParameters` MyBatis 위임, core 모듈 테스트 신설.

**Architecture:** 정적 전역 상태(`XRestEnvironment.getInstance()`)를 제거하고 Spring DI로 전환한다. `SessionData`는 Jackson POJO라 주입이 불가능하므로 만료 판단 책임을 `XAccessTokenParseHandler`로 옮긴다. 사용자가 상속하는 `XBaseAccessTokenHandler`는 서브클래스 호환을 위해 생성자 주입 대신 `@Value` 필드 주입을 쓴다.

**Tech Stack:** Java 17, Spring Boot 3.3.1, MyBatis 3.0.3, Gradle (멀티모듈: core / rest-api / mybatis / demo), JUnit 5 + AssertJ (`spring-boot-starter-test`, core에 이미 존재)

**설계 문서:** `docs/superpowers/specs/2026-07-17-high-priority-design.md`

---

## File Structure

**신규 생성**

| 파일 | 책임 |
|---|---|
| `core/src/test/java/one/axim/framework/core/utils/NamingConvertTest.java` | `NamingConvert` 3개 메서드 단위 테스트 |
| `core/src/test/java/one/axim/framework/core/data/XPaginationTest.java` | offset/page 우선순위, hasLimit 경계 고정 |
| `core/src/test/java/one/axim/framework/core/data/XPageTest.java` | `setPageRowsByObject` 타입 안전성, `getHasNext` 경계 |
| `rest-api/src/test/java/one/axim/framework/rest/model/SessionDataTest.java` | `isExpire(int)` 만료 경계 |
| `rest-api/src/main/java/one/axim/framework/rest/configuration/XSessionConfigurationValidator.java` | 기동 시 인증 설정 검증 (fail-fast) |

**수정**

| 파일 | 변경 내용 |
|---|---|
| `core/.../utils/NamingConvert.java` | `toCamelCase` 연속 언더스코어 버그 수정 |
| `core/.../data/XPagination.java` | `getOffset()` 우선순위 Javadoc 명시 |
| `mybatis/.../repository/IXRepository.java` | `K saveAll` → `int saveAll` |
| `mybatis/.../proxy/XRepositoryProxy.java` | `handleSaveAll` 반환 타입 정합 |
| `mybatis/.../plugin/XResultInterceptor.java` | `setParameters` 삭제 → MyBatis 위임 |
| `rest-api/.../configuration/XRestEnvironment.java` | 정적 싱글톤 제거 |
| `rest-api/.../model/SessionData.java` | `isExpire(int expireDays)`, 환경 의존 제거 |
| `rest-api/.../handler/XAccessTokenParseHandler.java` | `getTokenExpireDays()` 추가 |
| `rest-api/.../handler/XBaseAccessTokenHandler.java` | `@Value` 주입, secret key 경고 |
| `rest-api/.../filters/XRequestFilter.java` | 생성자 주입, 파라미터 마스킹 |
| `rest-api/.../proxy/XRestClientProxy.java` | 생성자 주입 |
| `rest-api/.../proxy/XRestClient.java` | 기본 스킴 `https://` |
| `TODO.md` | 처리 항목 정리 |
| `build.gradle` | 버전 1.4.0 |

**작업 순서 근거:** Task 1-3(core)은 다른 모듈에 의존하지 않으므로 먼저 한다. Task 4-5(mybatis)는 독립적이다. Task 6-9(rest-api)는 `XRestEnvironment` 제거가 여러 파일에 걸치므로 한 덩어리로 묶되, 컴파일이 깨진 중간 상태를 남기지 않도록 Task 6에서 사용처를 모두 전환한 뒤 마지막에 `getInstance()`를 지운다.

---

### Task 1: NamingConvert.toCamelCase 연속 언더스코어 수정

**Files:**
- Create: `core/src/test/java/one/axim/framework/core/utils/NamingConvertTest.java`
- Modify: `core/src/main/java/one/axim/framework/core/utils/NamingConvert.java:16-31`

- [ ] **Step 1: 실패하는 테스트 작성**

`core/src/test/java/one/axim/framework/core/utils/NamingConvertTest.java`:

```java
package one.axim.framework.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamingConvertTest {

    @Test
    @DisplayName("toCamelCase: 단일 언더스코어는 다음 글자를 대문자로 만든다")
    void toCamelCase_singleUnderscore() {
        assertThat(NamingConvert.toCamelCase("user_name")).isEqualTo("userName");
        assertThat(NamingConvert.toCamelCase("created_at_date")).isEqualTo("createdAtDate");
    }

    @Test
    @DisplayName("toCamelCase: 연속 언더스코어를 하나로 취급한다")
    void toCamelCase_consecutiveUnderscores() {
        assertThat(NamingConvert.toCamelCase("a__b")).isEqualTo("aB");
        assertThat(NamingConvert.toCamelCase("a___b")).isEqualTo("aB");
    }

    @Test
    @DisplayName("toCamelCase: 선행/후행 언더스코어 동작을 보존한다")
    void toCamelCase_leadingAndTrailingUnderscore() {
        assertThat(NamingConvert.toCamelCase("_abc")).isEqualTo("Abc");
        assertThat(NamingConvert.toCamelCase("abc_")).isEqualTo("abc");
    }

    @Test
    @DisplayName("toCamelCase: 언더스코어가 없으면 그대로 반환한다")
    void toCamelCase_noUnderscore() {
        assertThat(NamingConvert.toCamelCase("username")).isEqualTo("username");
    }

    @Test
    @DisplayName("toCamelCase: null과 빈 문자열은 그대로 반환한다")
    void toCamelCase_nullAndEmpty() {
        assertThat(NamingConvert.toCamelCase(null)).isNull();
        assertThat(NamingConvert.toCamelCase("")).isEmpty();
    }

    @Test
    @DisplayName("toUnderScoreName: 카멜케이스를 스네이크케이스로 바꾼다")
    void toUnderScoreName_basic() {
        assertThat(NamingConvert.toUnderScoreName("userName")).isEqualTo("user_name");
        assertThat(NamingConvert.toUnderScoreName("createdAtDate")).isEqualTo("created_at_date");
    }

    @Test
    @DisplayName("toUnderScoreName: 연속 대문자 약어를 처리한다")
    void toUnderScoreName_consecutiveUpperCase() {
        assertThat(NamingConvert.toUnderScoreName("XMLHttpRequest")).isEqualTo("xml_http_request");
    }

    @Test
    @DisplayName("toUnderScoreName: null과 빈 문자열은 그대로 반환한다")
    void toUnderScoreName_nullAndEmpty() {
        assertThat(NamingConvert.toUnderScoreName(null)).isNull();
        assertThat(NamingConvert.toUnderScoreName("")).isEmpty();
    }

    @Test
    @DisplayName("toCamelCaseByClassName: 스네이크케이스를 파스칼케이스로 바꾼다")
    void toCamelCaseByClassName_basic() {
        assertThat(NamingConvert.toCamelCaseByClassName("user_account")).isEqualTo("UserAccount");
    }

    @Test
    @DisplayName("toCamelCaseByClassName: 빈 파트를 건너뛴다")
    void toCamelCaseByClassName_emptyParts() {
        assertThat(NamingConvert.toCamelCaseByClassName("user__account")).isEqualTo("UserAccount");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :core:test --tests "*NamingConvertTest*"`

Expected: FAIL — `toCamelCase_consecutiveUnderscores`에서 `expected "aB" but was "a_b"`.
나머지 테스트는 통과해야 한다. 만약 다른 테스트가 실패하면 현재 동작을 잘못 파악한 것이니 멈추고 보고할 것.

- [ ] **Step 3: 최소 구현**

`NamingConvert.java`의 `toCamelCase`를 통째로 교체:

```java
    public static String toCamelCase(String value) {
        if (value == null || value.isEmpty()) return value;

        StringBuilder sb = new StringBuilder(value.length());
        boolean upperNext = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '_') {
                upperNext = true;
                continue;
            }

            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }

        return sb.toString();
    }
```

기존의 `StringBuilder`를 순회하며 `deleteCharAt`하는 방식은 인덱스가 밀려 연속 언더스코어를 놓친다. 한 번 훑으며 새 버퍼에 쌓는 방식이 그 문제를 구조적으로 없앤다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :core:test --tests "*NamingConvertTest*"`
Expected: PASS (10개 테스트 전부)

- [ ] **Step 5: 회귀 확인**

Run: `./gradlew build -x :demo:test`
Expected: BUILD SUCCESSFUL — `toCamelCase`는 컬럼→필드 매핑에 쓰이므로 다른 모듈이 깨지지 않는지 확인한다.

- [ ] **Step 6: 커밋**

```bash
git add core/src/main/java/one/axim/framework/core/utils/NamingConvert.java core/src/test/java/one/axim/framework/core/utils/NamingConvertTest.java
git commit -m "fix: NamingConvert.toCamelCase 연속 언더스코어 처리 (a__b → aB)"
```

---

### Task 2: XPagination 테스트 + offset 우선순위 문서화

**Files:**
- Create: `core/src/test/java/one/axim/framework/core/data/XPaginationTest.java`
- Modify: `core/src/main/java/one/axim/framework/core/data/XPagination.java:52-58`

**주의:** `getOffset()`의 **동작은 바꾸지 않는다**. 현재 동작을 테스트로 고정하고 Javadoc으로 규칙을 드러내기만 한다. 동작을 바꾸면 기존 사용자의 페이징 결과가 조용히 달라진다.

- [ ] **Step 1: 현재 동작을 고정하는 테스트 작성**

`core/src/test/java/one/axim/framework/core/data/XPaginationTest.java`:

```java
package one.axim.framework.core.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XPaginationTest {

    @Test
    @DisplayName("기본값은 page=1, size=20이다")
    void defaults() {
        XPagination pagination = new XPagination();

        assertThat(pagination.getPage()).isEqualTo(XPagination.DEFAULT_PAGE);
        assertThat(pagination.getSize()).isEqualTo(XPagination.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("첫 페이지의 offset은 0이다")
    void getOffset_firstPage() {
        XPagination pagination = new XPagination();

        assertThat(pagination.getOffset()).isZero();
    }

    @Test
    @DisplayName("offset은 (page - 1) * size로 계산된다")
    void getOffset_calculatedFromPageAndSize() {
        XPagination pagination = new XPagination();
        pagination.setPage(3);
        pagination.setSize(20);

        assertThat(pagination.getOffset()).isEqualTo(40);
    }

    @Test
    @DisplayName("page/size가 유효하면 setOffset으로 넣은 값은 무시된다")
    void getOffset_pageAndSizeTakePrecedenceOverSetOffset() {
        XPagination pagination = new XPagination();
        pagination.setOffset(999);

        // page=1, size=20 (기본값)이 유효하므로 계산값 0이 우선한다
        assertThat(pagination.getOffset()).isZero();
    }

    @Test
    @DisplayName("size가 0이면 setOffset으로 넣은 값이 쓰인다")
    void getOffset_fallsBackToOffsetWhenSizeIsZero() {
        XPagination pagination = new XPagination();
        pagination.setSize(0);
        pagination.setOffset(999);

        assertThat(pagination.getOffset()).isEqualTo(999);
    }

    @Test
    @DisplayName("page가 0이면 setOffset으로 넣은 값이 쓰인다")
    void getOffset_fallsBackToOffsetWhenPageIsZero() {
        XPagination pagination = new XPagination();
        pagination.setPage(0);
        pagination.setOffset(999);

        assertThat(pagination.getOffset()).isEqualTo(999);
    }

    @Test
    @DisplayName("hasLimit: size와 offset이 모두 0일 때만 false다")
    void hasLimit() {
        XPagination defaults = new XPagination();
        assertThat(defaults.hasLimit()).isTrue();

        XPagination noLimit = new XPagination();
        noLimit.setSize(0);
        noLimit.setOffset(0);
        assertThat(noLimit.hasLimit()).isFalse();

        XPagination offsetOnly = new XPagination();
        offsetOnly.setSize(0);
        offsetOnly.setOffset(10);
        assertThat(offsetOnly.hasLimit()).isTrue();
    }

    @Test
    @DisplayName("addOrder: null을 거부한다")
    void addOrder_rejectsNull() {
        XPagination pagination = new XPagination();

        assertThatThrownBy(() -> pagination.addOrder(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getOrders: 수정 불가능한 리스트를 반환한다")
    void getOrders_isUnmodifiable() {
        XPagination pagination = new XPagination();
        pagination.addOrder(new XOrder("createdAt", XDirection.DESC));

        assertThat(pagination.hasOrder()).isTrue();
        assertThatThrownBy(() -> pagination.getOrders().add(new XOrder("id", XDirection.ASC)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — 전부 통과해야 한다**

Run: `./gradlew :core:test --tests "*XPaginationTest*"`

Expected: PASS (9개 전부). 이 테스트는 현재 동작을 기록하는 것이므로 처음부터 통과한다.
실패한다면 현재 동작을 잘못 파악한 것이니 **코드를 고치지 말고** 멈추고 보고할 것.

`XOrder` 생성자 시그니처가 `XOrder(String, XDirection)`이 아니면 실제 시그니처에 맞춰 테스트를 조정할 것.

- [ ] **Step 3: getOffset Javadoc 추가**

`XPagination.java:52`의 `getOffset()` 위에 추가:

```java
    /**
     * 조회 시작 위치를 반환한다.
     *
     * <p><strong>우선순위:</strong> {@code page > 0 && size > 0}이면 항상
     * {@code (page - 1) * size}로 계산한 값을 반환하며, {@link #setOffset(int)}으로
     * 설정한 값은 무시된다. {@code page} 기본값이 1, {@code size} 기본값이 20이므로
     * 기본 상태에서는 계산식이 항상 적용된다.</p>
     *
     * <p>{@code setOffset()} 값을 쓰려면 {@code page} 또는 {@code size}를 0으로
     * 설정해 계산식을 비활성화해야 한다.</p>
     *
     * @return 조회 시작 위치 (0-based)
     */
```

- [ ] **Step 4: 커밋**

```bash
git add core/src/main/java/one/axim/framework/core/data/XPagination.java core/src/test/java/one/axim/framework/core/data/XPaginationTest.java
git commit -m "test: XPagination 테스트 추가, getOffset 우선순위 규칙 문서화"
```

---

### Task 3: XPage 테스트

**Files:**
- Create: `core/src/test/java/one/axim/framework/core/data/XPageTest.java`

- [ ] **Step 1: 테스트 작성**

`core/src/test/java/one/axim/framework/core/data/XPageTest.java`:

```java
package one.axim.framework.core.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XPageTest {

    @Test
    @DisplayName("setPageRowsByObject: List를 받으면 그대로 설정한다")
    void setPageRowsByObject_withList() {
        XPage<String> page = new XPage<>();
        page.setPageRowsByObject(List.of("a", "b"));

        assertThat(page.getPageRows()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("setPageRowsByObject: null을 받으면 null로 설정한다")
    void setPageRowsByObject_withNull() {
        XPage<String> page = new XPage<>();
        page.setPageRowsByObject(null);

        assertThat(page.getPageRows()).isNull();
    }

    @Test
    @DisplayName("setPageRowsByObject: List가 아니면 IllegalArgumentException을 던진다")
    void setPageRowsByObject_withNonList() {
        XPage<String> page = new XPage<>();

        assertThatThrownBy(() -> page.setPageRowsByObject("not a list"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected List");
    }

    @Test
    @DisplayName("addPageRows: 기존 행에 이어붙인다")
    void addPageRows_appends() {
        XPage<String> page = new XPage<>();
        page.setPageRows(List.of("a"));
        page.addPageRows(List.of("b", "c"));

        assertThat(page.getPageRows()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("addPageRows: 기존 행이 없으면 그대로 설정한다")
    void addPageRows_whenEmpty() {
        XPage<String> page = new XPage<>();
        page.addPageRows(List.of("a"));

        assertThat(page.getPageRows()).containsExactly("a");
    }

    @Test
    @DisplayName("addPageRows: null이나 빈 리스트는 무시한다")
    void addPageRows_ignoresNullAndEmpty() {
        XPage<String> page = new XPage<>();
        page.setPageRows(List.of("a"));

        page.addPageRows(null);
        page.addPageRows(List.of());

        assertThat(page.getPageRows()).containsExactly("a");
    }

    @Test
    @DisplayName("getHasNext: 다음 페이지가 있으면 true다")
    void getHasNext_whenMorePagesExist() {
        XPage<String> page = new XPage<>();
        page.setPage(1);
        page.setSize(20);
        page.setTotalCount(50);

        assertThat(page.getHasNext()).isTrue();
    }

    @Test
    @DisplayName("getHasNext: 마지막 페이지면 false다")
    void getHasNext_onLastPage() {
        XPage<String> page = new XPage<>();
        page.setPage(3);
        page.setSize(20);
        page.setTotalCount(50);

        assertThat(page.getHasNext()).isFalse();
    }

    @Test
    @DisplayName("getHasNext: 전체가 한 페이지에 들어가면 false다")
    void getHasNext_whenSinglePage() {
        XPage<String> page = new XPage<>();
        page.setPage(1);
        page.setSize(20);
        page.setTotalCount(20);

        assertThat(page.getHasNext()).isFalse();
    }

    @Test
    @DisplayName("getHasNext: 결과가 없으면 false다")
    void getHasNext_whenEmpty() {
        XPage<String> page = new XPage<>();
        page.setPage(1);
        page.setSize(20);
        page.setTotalCount(0);

        assertThat(page.getHasNext()).isFalse();
    }

    @Test
    @DisplayName("getHasNext: 필드가 설정되지 않았으면 false다")
    void getHasNext_withUnsetFields() {
        assertThat(new XPage<String>().getHasNext()).isFalse();

        XPage<String> noTotal = new XPage<>();
        noTotal.setPage(1);
        noTotal.setSize(20);
        assertThat(noTotal.getHasNext()).isFalse();

        XPage<String> zeroSize = new XPage<>();
        zeroSize.setPage(1);
        zeroSize.setSize(0);
        zeroSize.setTotalCount(50);
        assertThat(zeroSize.getHasNext()).isFalse();
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `./gradlew :core:test --tests "*XPageTest*"`
Expected: PASS (11개 전부). 실패하면 코드를 고치지 말고 보고할 것.

- [ ] **Step 3: 커밋**

```bash
git add core/src/test/java/one/axim/framework/core/data/XPageTest.java
git commit -m "test: XPage 타입 안전성 및 getHasNext 경계 테스트 추가"
```

---

### Task 4: saveAll 반환 타입 int로 수정

**Files:**
- Modify: `mybatis/src/main/java/one/axim/framework/mybatis/repository/IXRepository.java:61-67`
- Modify: `mybatis/src/main/java/one/axim/framework/mybatis/proxy/XRepositoryProxy.java:215-230`

**배경:** `UserRepository extends IXRepository<Integer, User>`이므로 `saveAll`은 `Integer` 반환으로 선언되는데 `handleSaveAll`은 `Long`을 반환한다. JDK 동적 프록시가 캐스팅하며 `ClassCastException`이 난다. 빈 리스트일 때만 `return 0`(Integer)이라 통과한다.

- [ ] **Step 1: IXRepository 시그니처 변경**

`IXRepository.java`의 `saveAll` 선언부(61-67행)를 교체:

```java
    /**
     * Batch insert using INSERT IGNORE. Duplicate-key rows are silently skipped.
     *
     * <p>배치 삽입에서는 중복 키 행이 건너뛰어지므로 "마지막 삽입 PK"는 의미가 모호하다.
     * 따라서 실제로 삽입된 행 수를 반환한다.</p>
     *
     * @param entities the list of entities to insert
     * @return the number of rows actually inserted
     */
    int saveAll(List<T> entities);
```

- [ ] **Step 2: handleSaveAll 반환 타입 정합**

`XRepositoryProxy.java`의 `handleSaveAll` 메서드를 교체:

```java
    private Object handleSaveAll(Object models) {
        if (!(models instanceof Iterable)) {
            throw new IllegalArgumentException("Argument for saveAll must be an Iterable.");
        }

        List<Object> modelList = new ArrayList<>();
        ((Iterable<?>) models).forEach(modelList::add);

        if (modelList.isEmpty()) {
            return 0;
        }

        XMapperParameter insertParameter = new XMapperParameter(modelList);
        insertParameter.setResultClass(entityMetadata.getModelClass());

        Long affectedRows = commonMapper.insertAll(insertParameter);
        return affectedRows == null ? 0 : affectedRows.intValue();
    }
```

`CommonMapper.insertAll()`의 `Long` 반환은 MyBatis 관례이므로 그대로 두고 프록시 경계에서 변환한다.

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :mybatis:compileJava :demo:compileJava`
Expected: BUILD SUCCESSFUL

`demo/src/main/java/one/axim/framework/demo/user/TestController.java:32`는 반환값을 쓰지 않으므로 수정이 필요 없다. 컴파일 에러가 나면 해당 호출부를 `int`에 맞게 고칠 것.

- [ ] **Step 4: 전체 빌드**

Run: `./gradlew build -x :demo:test`
Expected: BUILD SUCCESSFUL

`:demo:test`를 제외하는 이유는 DB 연결이 필요한 통합 테스트이기 때문이다.

- [ ] **Step 5: 커밋**

```bash
git add mybatis/src/main/java/one/axim/framework/mybatis/repository/IXRepository.java mybatis/src/main/java/one/axim/framework/mybatis/proxy/XRepositoryProxy.java
git commit -m "fix!: saveAll 반환 타입 K → int (ClassCastException 수정)"
```

---

### Task 5: XResultInterceptor.setParameters MyBatis 위임

**Files:**
- Modify: `mybatis/src/main/java/one/axim/framework/mybatis/plugin/XResultInterceptor.java:234`, `256-310` 부근

**배경:** `setParameters()`는 MyBatis `DefaultParameterHandler` 내부 로직을 손으로 복제한 것이라 MyBatis 버전 업그레이드 시 깨진다. `XResultInterceptor`는 `Executor.query`/`Executor.update`만 인터셉트하므로(`@Intercepts`, 63-67행) `newParameterHandler()`가 적용하는 `ParameterHandler` 플러그인 체인과 겹치지 않는다 — 재귀 위험이 없다.

- [ ] **Step 1: 호출부를 위임으로 교체**

`XResultInterceptor.java:234`의 호출:

```java
            setParameters(countStmt, mappedStatement, countBS, parameterObject);
```

를 다음으로 교체:

```java
            mappedStatement.getConfiguration()
                    .newParameterHandler(mappedStatement, parameterObject, countBS)
                    .setParameters(countStmt);
```

- [ ] **Step 2: setParameters 메서드 전체 삭제**

`public void setParameters(PreparedStatement ps, MappedStatement mappedStatement, BoundSql boundSql, Object parameterObject)` 메서드 전체(약 45줄)를 삭제한다.

- [ ] **Step 3: 미사용 import 정리**

삭제 후 쓰이지 않게 된 import를 제거한다. 다음 import들이 후보이며, **파일 내 다른 곳에서 쓰이는지 반드시 확인하고 지울 것**:

`ErrorContext`, `ParameterMapping`, `ParameterMode`, `TypeHandler`, `TypeHandlerRegistry`, `PropertyTokenizer`, `ForEachSqlNode`, `ExecutorException`, `ParameterHandler`, `MetaObject`

확인 방법: 각 심볼에 대해 `grep -n "<심볼>" mybatis/src/main/java/one/axim/framework/mybatis/plugin/XResultInterceptor.java` 실행 후 import 줄만 남으면 삭제한다.

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :mybatis:compileJava`
Expected: BUILD SUCCESSFUL, 경고 없음

- [ ] **Step 5: 전체 빌드**

Run: `./gradlew build -x :demo:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add mybatis/src/main/java/one/axim/framework/mybatis/plugin/XResultInterceptor.java
git commit -m "refactor: XResultInterceptor.setParameters를 MyBatis ParameterHandler에 위임"
```

---

### Task 6: XRestEnvironment 정적 싱글톤 제거

**Files:**
- Modify: `rest-api/src/main/java/one/axim/framework/rest/model/SessionData.java:51-69`
- Modify: `rest-api/src/main/java/one/axim/framework/rest/handler/XAccessTokenParseHandler.java:29-43`
- Modify: `rest-api/src/main/java/one/axim/framework/rest/handler/XBaseAccessTokenHandler.java:102-108`
- Modify: `rest-api/src/main/java/one/axim/framework/rest/filters/XRequestFilter.java:45-49`
- Modify: `rest-api/src/main/java/one/axim/framework/rest/proxy/XRestClientProxy.java:192`
- Modify: `rest-api/src/main/java/one/axim/framework/rest/configuration/XRestEnvironment.java:15,36,39-41`

**순서가 중요하다.** 사용처를 모두 전환한 뒤 마지막에 `getInstance()`를 지운다. 그래야 중간에 컴파일이 깨지지 않는다.

- [ ] **Step 1: SessionData에서 환경 의존 제거**

`SessionData.java`의 `import one.axim.framework.rest.configuration.XRestEnvironment;`를 삭제하고, `isExpire()`를 교체:

```java
    /**
     * 토큰 생성 시각으로부터 {@code expireDays}일이 지났는지 판단한다.
     *
     * @param expireDays 토큰 유효 일수
     * @return 만료되었거나 {@code createDate}를 해석할 수 없으면 true
     */
    @JsonIgnore
    public boolean isExpire(int expireDays) {

        try {
            LocalDateTime tokenDt = LocalDateTime.parse(this.createDate, FORMAT);
            return tokenDt.plusDays(expireDays).isBefore(LocalDateTime.now());
        } catch (Exception e) {
            return true;
        }
    }
```

`DEFAULT_TOKEN_EXPIRE_DAYS` 상수는 `XAccessTokenParseHandler`로 옮기므로 `SessionData`에서 삭제한다.

무인자 `isExpire()` 오버로드는 **남기지 않는다**. 남기면 설정을 무시하고 90일로 고정 동작해 조용히 잘못된 결과를 낸다. 컴파일 에러가 조용한 오동작보다 낫다.

- [ ] **Step 2: XAccessTokenParseHandler에 만료 일수 제공 메서드 추가**

`XAccessTokenParseHandler.java`에 추가하고 `validateSession`을 수정:

```java
    /** 액세스 토큰 기본 유효 일수 */
    int DEFAULT_TOKEN_EXPIRE_DAYS = 90;

    /**
     * 액세스 토큰 유효 일수를 반환한다. 구현체가 설정값으로 오버라이드할 수 있다.
     *
     * @return 토큰 유효 일수
     */
    default int getTokenExpireDays() {
        return DEFAULT_TOKEN_EXPIRE_DAYS;
    }
```

`validateSession`의 38행:

```java
        if (session.isExpire()) {
```

을 다음으로 교체:

```java
        if (session.isExpire(getTokenExpireDays())) {
```

- [ ] **Step 3: XBaseAccessTokenHandler를 @Value 주입으로 전환**

`XBaseAccessTokenHandler.java`에서 `import one.axim.framework.rest.configuration.XRestEnvironment;`를 삭제하고, 다음 import를 추가:

```java
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
```

`private String getSecretKey()` 메서드(102-108행)를 삭제하고, 클래스 필드부에 추가:

```java
    @Value("${axim.rest.session.secret-key:#{null}}")
    private String secretKey;

    @Value("${axim.rest.session.token-expire-days:90}")
    private int tokenExpireDays;

    @PostConstruct
    void warnIfSecretKeyMissing() {
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("axim.rest.session.secret-key is not configured. "
                    + "Access tokens are issued as unsigned plain Base64 and can be forged by anyone. "
                    + "Configure a secret key before deploying to production.");
        }
    }

    /**
     * 토큰 서명에 쓸 secret key를 반환한다. 미설정이면 null.
     * 서브클래스가 오버라이드해 다른 소스에서 공급할 수 있다.
     */
    protected String getSecretKey() {
        return secretKey;
    }

    @Override
    public int getTokenExpireDays() {
        return tokenExpireDays;
    }
```

**생성자 주입을 쓰지 않는 이유:** 사용자가 이 클래스를 상속해 빈으로 등록하는 것이 프레임워크의 확장 지점이다. 생성자에 파라미터를 추가하면 기존 서브클래스가 전부 컴파일 에러가 난다. `@Value` 필드 주입은 무인자 생성자를 유지하면서 정적 전역 상태를 제거한다.

기존 `getSecretKey()` 호출부(44행, 64행)는 시그니처가 같으므로 수정할 필요가 없다.

- [ ] **Step 4: XRequestFilter를 생성자 주입으로 전환**

`XRequestFilter.java`에 생성자를 추가하고 정적 접근을 제거한다.

필드부에 추가:

```java
    private final XRestEnvironment environment;

    public XRequestFilter(XRestEnvironment environment) {
        this.environment = environment;
    }
```

45-49행:

```java
            XRestEnvironment env = XRestEnvironment.getInstance();
            if (env != null) {
                MDC.put("LOCAL_IP", env.getServerIp());
                MDC.put("LOCAL_HOSTNAME", env.getServerHostName());
            }
```

를 다음으로 교체:

```java
            MDC.put("LOCAL_IP", environment.getServerIp());
            MDC.put("LOCAL_HOSTNAME", environment.getServerHostName());
```

71행의 `if (env != null && env.isDevelop()) {`를 다음으로 교체:

```java
            if (environment.isDevelop()) {
```

주입된 빈은 null일 수 없으므로 null 체크가 사라진다 — 이것이 DI 전환의 실질적 이득이다.

- [ ] **Step 5: XRestClientProxy를 주입으로 전환**

`XRestClientProxy.java:192`:

```java
                    host = XRestEnvironment.getInstance().resolvePlaceholders(host);
```

`XRestClientProxy`는 이미 `@Autowired ApplicationContext`를 갖고 있다. 필드에 `XRestEnvironment`를 주입하도록 추가한다:

```java
    @Autowired
    private XRestEnvironment xRestEnvironment;
```

그리고 192행을 교체:

```java
                    host = xRestEnvironment.resolvePlaceholders(host);
```

기존 `@Autowired` 필드들과 같은 방식을 따른다. 이 클래스가 이미 필드 주입을 쓰고 있으면 그 패턴을 유지하고, 생성자 주입을 쓰고 있으면 생성자에 파라미터를 추가할 것.

- [ ] **Step 6: XRestEnvironment에서 정적 싱글톤 삭제**

`XRestEnvironment.java`에서 다음 3개를 삭제:

1. 15행: `private static volatile XRestEnvironment instance;`
2. 36행: `instance = this;` (생성자 안)
3. 39-41행: `getInstance()` 메서드 전체

`@Component`, 생성자, 나머지 메서드는 그대로 둔다.

- [ ] **Step 7: 정적 접근이 완전히 사라졌는지 확인**

Run: `grep -rn "XRestEnvironment.getInstance\|getInstance()" core rest-api mybatis demo --include=*.java`
Expected: **0건**

Run: `./gradlew build -x :demo:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 커밋**

```bash
git add rest-api/src/main/java/one/axim/framework/rest/
git commit -m "refactor!: XRestEnvironment 정적 싱글톤 제거, Spring DI로 전환

- SessionData.isExpire()에서 환경 의존 제거, isExpire(int expireDays)로 변경
- XAccessTokenParseHandler.getTokenExpireDays() 추가
- XBaseAccessTokenHandler를 @Value 주입으로 전환 (서브클래스 호환 유지)
- XRequestFilter, XRestClientProxy를 주입으로 전환"
```

---

### Task 7: SessionData 테스트

**Files:**
- Create: `rest-api/src/test/java/one/axim/framework/rest/model/SessionDataTest.java`

Task 6에서 `SessionData`가 순수 데이터 객체가 되면서 처음으로 단위 테스트가 가능해졌다. 이 테스트가 리팩터링의 성과를 증명한다.

- [ ] **Step 1: rest-api에 테스트 의존성이 있는지 확인**

Run: `grep -n "testImplementation" rest-api/build.gradle`

`spring-boot-starter-test`가 없으면 `rest-api/build.gradle`의 `dependencies` 블록에 추가:

```gradle
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
```

- [ ] **Step 2: 테스트 작성**

`rest-api/src/test/java/one/axim/framework/rest/model/SessionDataTest.java`:

```java
package one.axim.framework.rest.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class SessionDataTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Test
    @DisplayName("새로 만든 세션은 만료되지 않는다")
    void isExpire_freshSession() {
        SessionData session = new SessionData("sid-1");

        assertThat(session.isExpire(90)).isFalse();
    }

    @Test
    @DisplayName("생성자는 sessionId와 createDate를 설정한다")
    void constructor_setsFields() {
        SessionData session = new SessionData("sid-1");

        assertThat(session.getSessionId()).isEqualTo("sid-1");
        assertThat(session.getCreateDate()).isNotBlank();
    }

    @Test
    @DisplayName("유효 일수를 넘긴 세션은 만료된다")
    void isExpire_oldSession() {
        SessionData session = new SessionData("sid-1");
        session.setCreateDate(LocalDateTime.now().minusDays(91).format(FORMAT));

        assertThat(session.isExpire(90)).isTrue();
    }

    @Test
    @DisplayName("유효 일수 안쪽의 세션은 만료되지 않는다")
    void isExpire_withinExpiry() {
        SessionData session = new SessionData("sid-1");
        session.setCreateDate(LocalDateTime.now().minusDays(89).format(FORMAT));

        assertThat(session.isExpire(90)).isFalse();
    }

    @Test
    @DisplayName("만료 일수는 인자로 받은 값을 따른다")
    void isExpire_respectsExpireDaysArgument() {
        SessionData session = new SessionData("sid-1");
        session.setCreateDate(LocalDateTime.now().minusDays(10).format(FORMAT));

        assertThat(session.isExpire(7)).isTrue();
        assertThat(session.isExpire(30)).isFalse();
    }

    @Test
    @DisplayName("createDate 형식이 잘못되면 만료로 처리한다")
    void isExpire_withMalformedCreateDate() {
        SessionData session = new SessionData("sid-1");
        session.setCreateDate("not-a-date");

        assertThat(session.isExpire(90)).isTrue();
    }

    @Test
    @DisplayName("createDate가 null이면 만료로 처리한다")
    void isExpire_withNullCreateDate() {
        SessionData session = new SessionData("sid-1");
        session.setCreateDate(null);

        assertThat(session.isExpire(90)).isTrue();
    }
}
```

- [ ] **Step 3: 테스트 실행**

Run: `./gradlew :rest-api:test --tests "*SessionDataTest*"`
Expected: PASS (7개 전부)

- [ ] **Step 4: 커밋**

```bash
git add rest-api/src/test/java/one/axim/framework/rest/model/SessionDataTest.java rest-api/build.gradle
git commit -m "test: SessionData.isExpire 만료 경계 테스트 추가"
```

---

### Task 8: Security — https 기본 스킴 + 파라미터 마스킹

**Files:**
- Modify: `rest-api/src/main/java/one/axim/framework/rest/proxy/XRestClient.java:308-314`
- Modify: `rest-api/src/main/java/one/axim/framework/rest/filters/XRequestFilter.java:20-22, 97-107`

secret key 경고는 Task 6 Step 3에서 이미 처리했다.

- [ ] **Step 1: makeServiceUrl 기본 스킴을 https로 변경**

`XRestClient.java`의 `makeServiceUrl`(308-314행)을 교체:

```java
    /**
     * 서비스 호스트와 경로를 합쳐 요청 URL을 만든다.
     *
     * <p>{@code serviceHost}에 스킴이 없으면 {@code https://}를 붙인다.
     * 평문 HTTP를 쓰려면 설정에 {@code http://}를 명시해야 한다.</p>
     */
    private String makeServiceUrl(String url) {
        if (this.serviceHost.startsWith("http://") || this.serviceHost.startsWith("https://")) {
            return this.serviceHost + (url.startsWith("/") ? "" : "/") + url;
        } else {
            return "https://" + this.serviceHost + (url.startsWith("/") ? "" : "/") + url;
        }
    }
```

- [ ] **Step 2: XRequestFilter에 민감 파라미터 목록 추가**

`XRequestFilter.java`의 `SENSITIVE_HEADERS`(20-22행) 바로 아래에 추가:

```java
    private static final Set<String> SENSITIVE_PARAMS = Set.of(
            "password", "passwd", "pwd", "secret", "token",
            "access-token", "accesstoken", "api-key", "apikey", "credential"
    );
```

이미 있는 `SENSITIVE_HEADERS` 패턴을 그대로 따른다.

- [ ] **Step 3: getRequestParameterString에 마스킹 적용**

`getRequestParameterString`(97-107행)을 교체:

```java
    private String getRequestParameterString(HttpServletRequest request) {

        final StringBuilder paramSb = new StringBuilder();
        Map<String, String[]> params = request.getParameterMap();

        params.forEach((name, values) -> {
            String value = SENSITIVE_PARAMS.contains(name.toLowerCase())
                    ? "***"
                    : String.join(", ", values);

            paramSb.append(String.format("%s => %s, ", name, value));
        });

        return paramSb.toString();
    }
```

- [ ] **Step 4: 빌드**

Run: `./gradlew build -x :demo:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add rest-api/src/main/java/one/axim/framework/rest/proxy/XRestClient.java rest-api/src/main/java/one/axim/framework/rest/filters/XRequestFilter.java
git commit -m "fix!: XRestClient 기본 스킴 https로 변경, 요청 파라미터 마스킹 추가"
```

---

### Task 9: XSessionResolver 인증 갭 — 기동 시 fail-fast

**Files:**
- Create: `rest-api/src/main/java/one/axim/framework/rest/configuration/XSessionConfigurationValidator.java`

**배경:** `XWebMvcConfiguration:23`이 `@Autowired(required = false)`라 사용자가 `XAccessTokenParseHandler` 빈을 등록하지 않으면 `XSessionResolver.resolveArgument()`가 `null`을 반환하고, 컨트롤러가 인증 없이 실행된다.

`XSessionController`를 상속하는 경로는 `@Autowired`(required=true)라 이미 기동 시 실패한다. 이 검증기는 `SessionData`를 **컨트롤러 메서드 파라미터**로 받는 경로만 다룬다.

- [ ] **Step 1: 검증기 작성**

`rest-api/src/main/java/one/axim/framework/rest/configuration/XSessionConfigurationValidator.java`:

```java
package one.axim.framework.rest.configuration;

import one.axim.framework.rest.handler.XAccessTokenParseHandler;
import one.axim.framework.rest.model.SessionData;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * 기동 시 세션 인증 설정을 검증한다.
 *
 * <p>{@link SessionData}를 파라미터로 받는 컨트롤러 메서드가 있는데
 * {@link XAccessTokenParseHandler} 빈이 등록되지 않았다면, 해당 파라미터는
 * 런타임에 조용히 {@code null}로 주입되어 인증 없이 요청이 처리된다.
 * 이 검증기는 그 상황을 기동 시점에 실패시킨다.</p>
 */
@Component
public class XSessionConfigurationValidator implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {

        ApplicationContext context = event.getApplicationContext();

        if (!context.getBeansOfType(XAccessTokenParseHandler.class).isEmpty()) {
            return;
        }

        RequestMappingHandlerMapping mapping =
                context.getBeanProvider(RequestMappingHandlerMapping.class).getIfAvailable();

        if (mapping == null) {
            return;
        }

        List<String> offenders = new ArrayList<>();

        for (HandlerMethod handlerMethod : mapping.getHandlerMethods().values()) {
            for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
                if (SessionData.class.isAssignableFrom(parameter.getParameterType())) {
                    offenders.add(handlerMethod.getBeanType().getName()
                            + "#" + handlerMethod.getMethod().getName());
                    break;
                }
            }
        }

        if (!offenders.isEmpty()) {
            throw new IllegalStateException(
                    "No XAccessTokenParseHandler bean is registered, but the following controller methods "
                            + "declare a SessionData parameter: " + String.join(", ", offenders)
                            + ". Without a handler these parameters resolve to null and the requests would be "
                            + "processed without authentication. Register an XAccessTokenParseHandler bean "
                            + "(e.g. a @Component extending XBaseAccessTokenHandler).");
        }
    }
}
```

`getBeanProvider(...).getIfAvailable()`을 쓰는 이유는 `RequestMappingHandlerMapping` 빈이 없는 비-웹 컨텍스트에서도 안전하게 넘어가기 위해서다.

- [ ] **Step 2: 빌드**

Run: `./gradlew build -x :demo:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: demo 앱이 여전히 기동하는지 확인**

Run: `grep -rn "XAccessTokenParseHandler\|XBaseAccessTokenHandler" demo/src/main/java`

demo에 핸들러 빈이 없고 `SessionData` 파라미터를 쓰는 컨트롤러도 없다면 검증기는 통과한다.
demo에 `SessionData` 파라미터를 쓰는 컨트롤러가 있는데 핸들러 빈이 없다면, **이는 검증기가 실제 설정 오류를 잡아낸 것이다** — demo에 핸들러 빈을 추가하고 그 사실을 보고할 것.

- [ ] **Step 4: 커밋**

```bash
git add rest-api/src/main/java/one/axim/framework/rest/configuration/XSessionConfigurationValidator.java
git commit -m "fix: 핸들러 빈 미등록 시 인증 우회를 기동 시점에 차단"
```

---

### Task 10: TODO.md 정리 및 버전 갱신

**Files:**
- Modify: `TODO.md`
- Modify: `build.gradle:12`

- [ ] **Step 1: TODO.md에서 처리한 항목 제거**

다음 항목들을 `TODO.md`에서 삭제한다 (완료됨):

High Priority / Architecture:
- `XRestEnvironment` 정적 싱글톤 제거
- `XResultInterceptor.setParameters()` MyBatis 내부 로직 복제
- `IXRepository.saveAll()` 반환 타입 불일치

High Priority / Security: 4개 항목 전부

High Priority / Code Quality:
- 테스트 작성 항목 (하위 3개 불릿 포함)

Medium Priority / Code Quality:
- `NamingConvert.toCamelCase` — 연속 언더스코어 처리 버그 수정

- [ ] **Step 2: TODO.md에 신규 발견 항목 추가**

Medium Priority / Security 섹션에 추가 (섹션이 없으면 만들 것):

```markdown
### Security
- [ ] `XRequestFilter` — develop 프로파일에서 JSON 요청 본문을 마스킹 없이 MDC에 기록 (password 등 평문 로깅)
```

`XPagination.getOffset()` 우선순위 문서화 항목이 Medium에 남아 있으면, Javadoc으로 처리했으므로 삭제한다.

- [ ] **Step 3: 버전을 1.4.0으로 올린다**

`build.gradle:12`:

```gradle
    version = '1.4.0'
```

breaking change가 4건(`isExpire()`, `saveAll()`, `makeServiceUrl` 스킴, `getInstance()` 제거)이므로 patch가 아니라 minor를 올린다.

- [ ] **Step 4: 최종 전체 빌드**

Run: `./gradlew clean build -x :demo:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 정적 접근 제거 최종 확인**

Run: `grep -rn "XRestEnvironment.getInstance" core rest-api mybatis demo --include=*.java`
Expected: 출력 없음

- [ ] **Step 6: 커밋**

```bash
git add TODO.md build.gradle
git commit -m "chore: High Priority 항목 정리, v1.4.0"
```

---

## 완료 조건

- [ ] `./gradlew clean build -x :demo:test`가 통과한다
- [ ] `grep -rn "XRestEnvironment.getInstance" core rest-api mybatis demo --include=*.java`가 0건이다
- [ ] 신규 테스트 37개가 모두 통과한다 (NamingConvert 10, XPagination 9, XPage 11, SessionData 7)
- [ ] `NamingConvert.toCamelCase` 수정이 TDD 순서(실패 테스트 → 수정)를 지켰음이 커밋에 드러난다

## Breaking Changes (릴리스 노트용)

| 변경 | 영향 | 대응 |
|---|---|---|
| `SessionData.isExpire()` 제거 | 직접 호출 코드 컴파일 에러 | `isExpire(int)` 사용 또는 `validateSession()` 경유 |
| `IXRepository.saveAll()` 반환 `K` → `int` | `K`가 `Long`이던 코드 컴파일 에러 | 반환값을 `int`로 받기 |
| `XRestClient` 기본 스킴 `https://` | 스킴 없이 HTTP 호출하던 설정 | 설정에 `http://` 명시 |
| `XRestEnvironment.getInstance()` 제거 | 정적 접근 코드 컴파일 에러 | `XRestEnvironment` 빈 주입 |
