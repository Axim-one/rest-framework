# TODO - REST Framework Improvements

## High Priority

---

## Medium Priority

### Code Quality
- [ ] `NamingConvert` 유틸리티 클래스 — `final` + `private` 생성자 추가
- [ ] `NamingConvert.toCamelCaseByClassName` — null 파라미터 체크 추가
- [ ] `XRestException` — `description`, `data` setter 제거, 불변 설계
- [ ] `XOrder` — 생성자에 `column`, `direction` null 검증 추가
- [ ] `XOrder.toString()` — SQL 변환과 디버그 표시 분리 (`toSql()` 별도 메서드)
- [ ] `XPage` — boxed `Integer` → primitive `int` 통일 (XPagination과 일관성)
- [ ] `XPage.setPageRowsByObject` — unchecked cast 타입 안전성 개선
- [ ] `XDataMap` — `putIfAbsent`, `compute`, `computeIfAbsent` 등 미오버라이드 메서드 추가
- [ ] `XRepositoryProxy.handleExists()` — `findById` 대신 `COUNT(*)` 사용으로 효율화
- [ ] `XRepositoryConfig` — static mutable 상태를 Spring Bean으로 전환
- [ ] `XDirection.fromString` — `catch (Exception)` → `catch (IllegalArgumentException)` 축소
- [ ] `getAllFields()` 중복 — `XResultInterceptor`와 `EntityMetadataFactory`에서 공유 유틸로 추출

### Security
- [ ] `XRequestFilter` — develop 프로파일에서 JSON 요청 본문을 마스킹 없이 MDC에 기록 (password 등 평문 로깅)

### API Design
- [ ] `XAbstractController.log()/error()` — 항상 `XAbstractController` 로거 사용, 서브클래스 로거 미반영
- [ ] `XExceptionHandler` — 4xx 예외를 `log.error()` → `log.warn()`으로 변경
- [ ] `UnAuthorizedException` → `UnauthorizedException` 네이밍 수정
- [ ] `IXRepository` — `deleteById`/`delete`/`remove` 중복 메서드 정리

### REST Client
- [ ] 응답 이중 역직렬화 제거 — `String` 변환 없이 직접 타입 역직렬화
- [ ] `XRestClient` 메서드 오버로드 18개 → Builder 패턴 고려
- [ ] `XRestClientProxy` — `@XRestAPI` 없는 메서드 호출 시 null 대신 `UnsupportedOperationException`

---

## Low Priority

### Dead Code 정리
- [ ] `XQueryParams` — 미사용 어노테이션
- [ ] `XRestGroupName` — 미사용 어노테이션
- [ ] `IXRequestHandler` — 미사용 인터페이스
- [ ] `XRestClientType` — 미사용 enum
- [ ] `WhereCondition` / `ConditionType` — 미사용 SQL 조건 클래스
- [ ] `XQueryValue` — 미사용 모델 클래스
- [ ] `IXRepository` — 미사용 `SelectProvider` import 제거

### Enhancement
- [ ] 파생 쿼리 확장 — `Or`, `In`, `Between`, `Like`, `IsNull`, `OrderBy` 지원
- [ ] DB 이식성 — Dialect 추상화 (현재 MySQL 전용)
- [ ] REST 클라이언트 — Retry, Circuit Breaker 패턴
- [ ] REST 클라이언트 — 비동기 지원 (`CompletableFuture`)
- [ ] `SessionData.createDate` — `String` → `Instant`/epoch로 변경 (시간대 문제)
- [ ] HTTP 연결 풀 — idle 연결 정리 정책 (`evictExpiredConnections`)
- [ ] `XPaginationDefault.direction` 기본값 — `DESC` → `ASC` 검토 (대부분 프레임워크 컨벤션)
- [ ] `ColumnSpec` — `Byte.class`/`boolean.class` 시각적 페어링 정리
- [ ] `XPage.getHasNext()` → `hasNext()` 메서드명 개선
