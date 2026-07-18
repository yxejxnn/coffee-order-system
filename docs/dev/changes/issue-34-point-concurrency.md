# 포인트 최초 생성 경로 동시성 구멍 제거

대상: point/charge
이슈: [#34](https://github.com/yxejxnn/coffee-order-system/issues/34)
담당: Claude

## 배경 / 요구
`PointService.getLockedPoint()`는 비관적 락 조회가 비면(=그 회원의 `Point`가 아직 없으면) `createPointForExistingMember`로 폴백해 회원 존재만 확인하고 그 자리에서 `Point`를 insert한다. 이 폴백엔 락이 없어, 같은 회원(Point 없는 불변식 파손 상태)이 동시에 2번 첫 충전을 요청하면 둘 다 insert를 시도해 `uk_member_id` 위반 → `DataIntegrityViolationException` → 500이 난다. `docs/dev/point/charge/design.md`에 이미 알려진 한계로 기록돼 있던 것.

## 설계
초안(같은 트랜잭션 안에서 insert 실패를 잡아 재조회)은 구현 중 두 종류의 MySQL 데드락으로 이어졌고, 그다음 시도(격리수준을 `READ_COMMITTED`로 낮춤)는 자체 리뷰에서 `order/create(#5)` 경로에 무효화됨이 드러났다(상세: `docs/logs/point/charge/002-concurrency-fix.md` Attempt 1~5). 최종 설계:
1. `Point` 생성을 `PointBootstrapService.ensurePointExists`(`REQUIRES_NEW`, 별도 트랜잭션)에 위임해, insert 시도(성공/duplicate-key 실패 모두)가 완전히 끝나고 커밋/롤백된 뒤에야 바깥 트랜잭션이 새로 락을 건다.
2. `getLockedPoint`가 `PointRepository#existsByMemberId`(잠금 없는 조회)로 먼저 존재를 확인하고, 없을 때만 위 생성 경로를 태운 뒤에 `SELECT ... FOR UPDATE`를 건다 — 이러면 그 시점엔 행이 항상 이미 존재해 갭 락 자체가 발생할 수 없다. **격리수준이나 호출자의 트랜잭션 전파 방식과 무관하게 안전**해서, `charge`/`use`에 별도 isolation 지정이 필요 없다.
3. `ensurePointExists`는 예외를 안에서 삼키지 않고 그대로 던져 Spring이 정상 롤백하게 하고, 호출자(`PointService`)가 `DataIntegrityViolationException`·`CannotAcquireLockException`을 잡아 무시한 뒤 재조회한다.

폴백 경로 자체를 없애는 대안(완료조건의 다른 선택지)은 시드 이전 회원에 대해 `MEMBER_NOT_FOUND` 회귀를 만들어 기각(`docs/dev/changes/issue-4-point-charge.md`에서 고친 버그가 되살아남).

## 자체 리뷰(`/code-review --comment`, high effort, 8각도) 반영
- **CONFIRMED(치명적)** — 격리수준(`READ_COMMITTED`) 기반의 첫 fix는 `OrderService.create()`가 이미 연 트랜잭션에 `use()`가 `REQUIRED`로 참여(join)하는 실제 주문결제 경로에서 Spring이 조용히 무시해 무효화됨. → 위 2번(잠금 없는 선확인)으로 재설계.
- **CONFIRMED** — `DataIntegrityViolationException`만으로는 락 대기 초과 시 `CannotAcquireLockException`이 빠져나갈 수 있음 → 함께 catch.
- **CONFIRMED**(컨벤션) — `PointBootstrapService`에 클래스 레벨 `@Transactional(readOnly = true)` 누락 → 추가.
- **PLAUSIBLE** — HikariCP `maximum-pool-size`가 이론적 최악치와 정확히 같아 여유 없음 → 20→25.
- **PLAUSIBLE**(테스트 정리) — FQN 인라인 사용 → import로 정리.
- **반영 안 함(트레이드오프로 기록)** — `REQUIRES_NEW` 분리로 생성이 독립 커밋되어, 이후 같은 요청이 실패해도 balance 0인 Point는 남음. 유효한 초기 상태라 정합성 위반 아님으로 판단.
- 신규: `OrderServiceConcurrencyTest`에 "Point 없는 회원으로 동시 첫 주문" 케이스 추가 — 첫 fix가 놓쳤던 실제 주문 경로를 검증.

## 관련 결정·질문
- `docs/dev/point/charge/design.md` (갱신 완료 — 격리수준 의존 설계의 무효화 경위와 최종 구조 반영)
- [ADR-001](../../adr/ADR-001-포인트-동시성제어.md)

## 태스크
- [x] `PointService`·`PointRepository#existsByMemberId`·`PointBootstrapService` 구현
- [x] `PointServiceConcurrencyTest` + `OrderServiceConcurrencyTest`에 "Point 없는 회원 동시 요청" 케이스 추가
- [x] 자체 리뷰 발견사항(격리수준 무효화 포함) 전부 반영
- [x] `docs/dev/point/charge/design.md` 갱신(SSOT)

## 평가(통과) 기준
- 신규 동시성 테스트: N개 동시 첫 충전 요청 → 예외 없이 완료, 최종 잔액 == N×amount, `Point` 행 정확히 1개, `PointHistory` 정확히 N건
- 검증 레벨: Level 1(단위+회귀) · Level 3(락·동시성 통합, 실제 MySQL 대상)
