# 002-validation-order — order/create 검증 정리 (로그)

## Attempt 1 — 2026-07-19  ✅ PASS
- 시도: (#35) `OrderService.create()`의 quantity 계산·검증을 메서드 맨 앞(회원·메뉴 조회보다 먼저)으로 이동. `OrderServiceTest`에 quantity+menuId가 동시에 무효인 케이스(`create_throwsInvalidQuantity_beforeCheckingMenuOrMember_whenBothAreInvalidToo`) 추가해 우선순위가 실제로 바뀌었는지 검증. 기존 `create_throwsInvalidQuantity_whenQuantityIsZeroOrNegative`의 이제 안 쓰는 `memberRepository`/`menuRepository` 스텁 제거(Mockito strict stubbing).
- (#42) 조사: `memberRepository.existsById` 중복이 실제로 제거 가능한지 검토. `PointService.ensurePointCreated`의 체크는 `PointController#charge`(사전 검증 없는 진입점)에 필수라 제거 불가. `OrderService` 쪽을 제거하면 (a) `OrderServiceTest`가 `PointService`를 mock하므로 회원 미존재 시나리오를 재현하려면 테스트를 다시 짜야 하고 (b) 회원 없는 요청에서도 `menuRepository.findById`가 먼저 실행되는 불필요한 조회 + `MEMBER_NOT_FOUND`/`MENU_NOT_FOUND` 우선순위 역전이라는 실질적 회귀가 생김 → 코드 변경 없이 결정만 `docs/dev/order/create/design.md`에 기록, 이슈 close.
- 결과: `./gradlew test --tests "*OrderServiceTest*"` PASS(신규 1건 포함). 전체 `./gradlew test`(Docker MySQL/Redis/Kafka 기동) 62건 전부 PASS, 회귀 없음.
- 검증 레벨: Level 1(단위+전체 회귀) PASS.
