# 001-list — 커피 메뉴 목록 조회 (로그)

## Attempt 1 — 2026-07-15  ✅ PASS
- 시도: `docs/api/menu.md` 계약대로 `MenuResponse`/`MenuService`/`MenuController` 구현 (기존 `Menu`/`MenuRepository` 재사용). 컨트롤러 등장이 처음이라 기존 `GlobalExceptionHandlerTest`/`DataSeederTest` 스타일(Spring 컨텍스트 없이 직접 인스턴스화 + Mockito)을 그대로 따름.
- 결과: `./gradlew test` 전체 통과(18개, `EntityMappingTest` 포함 실 MySQL 대상). `MenuServiceTest` 2건, `MenuControllerTest` 2건 모두 PASS.
- 검증 레벨: 단위 테스트(Service/Controller) PASS · **Level 6(실제 HTTP)** PASS. Level 3(락·동시성)·Level 4(메시징·캐시)는 단순 조회라 해당 없음(`docs/dev/ongoing/issue-3-menu-list.md` 참조).
- 증거(API 샘플): `docker-compose up -d mysql` → `./gradlew bootRun` → `curl http://localhost:8080/api/menus`
  ```json
  {"code":"SUCCESS","data":[{"id":3,"name":"아메리카노","price":4500},{"id":4,"name":"카페라떼","price":5000},{"id":5,"name":"카푸치노","price":5000},{"id":6,"name":"바닐라라떼","price":5500},{"id":7,"name":"콜드브루","price":5000}]}
  ```
  `DataSeeder`가 시드한 메뉴 5건이 그대로 응답됨. 빈 목록(`[]`) 케이스는 단위 테스트로만 확인(시드 후 실제 빈 테이블 상태를 만들지 않음 — 로직상 `findAll()`이 빈 리스트를 반환하면 그대로 빈 배열 직렬화되므로 리스크 낮음).

## Attempt 2 — 2026-07-15  ✅ PASS
- 시도: PR #19 자체 리뷰(`/code-review --comment`, 8개 관점 서브에이전트 실행 + 후보 2건 검증). `MenuControllerTest`가 `containsExactly(menu)`로 객체 identity에만 의존한다는 CONFIRMED(낮은 심각도) 발견 → 개별 필드(id/name/price) 비교로 수정. `findAll()` 정렬 미보장 후보는 `docs/api/menu.md`에 정렬 요구사항이 없어 REFUTED.
- 결과: `./gradlew test --tests "*MenuControllerTest*"` PASS.
- PR 인라인 코멘트: https://github.com/yxejxnn/coffee-order-system/pull/19#discussion_r3587537209 → 반영 후 resolve.
