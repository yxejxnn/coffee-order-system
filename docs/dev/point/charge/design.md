# point/charge — Design

## 개요
회원의 포인트 잔액을 충전한다. 동일 회원에 대한 동시 충전 요청에서도 잔액 정합성(lost update 없음)을 보장하며, 이 이슈에서 확정한 비관적 락 리포지토리 패턴을 order/create(#5)가 그대로 재사용한다.

## API / 인터페이스
- `POST /api/points/charge` — 상세 계약(요청/응답/에러 코드)은 `docs/api/point.md` 참조.
- 구현: `domain/point/controller/PointController` → `domain/point/service/PointService#charge` → `domain/point/repository/PointRepository#findByMemberIdForUpdate`(`@Lock(PESSIMISTIC_WRITE)`) → `domain/point/entity/Point#charge` + `domain/point/repository/PointHistoryRepository`.
- 검증 순서: `amount <= 0`(또는 null, 단 null은 컨트롤러 `@Valid`의 `@NotNull`이 먼저 가로채 `COMMON_001`로 응답) → `INVALID_AMOUNT`(400) 먼저 확인 후, `PointRepository#existsByMemberId`(잠금 없는 조회)로 존재를 먼저 확인 → 없으면 `MemberRepository#existsById`로 회원 존재를 확인해 없으면 `MEMBER_NOT_FOUND`(404), 있으면(불변식이 깨진 예외 상황) `PointBootstrapService`(별도 트랜잭션)에 위임해 그 자리에서 `Point`를 만든 뒤 → (존재가 확정된 상태에서) `POINT` 락 조회로 이어간다(#34, 아래 참고).

## 데이터 모델
- `points.balance`를 증가시키고(`Math.addExact`로 오버플로우 시 예외 — 조용한 wrap 방지), 같은 트랜잭션에서 `point_histories`에 `CHARGE` 이력 1건을 남긴다(`order_group_id`는 null). 상세 스펙: `docs/db/point.md`, `docs/db/point-history.md`.
- `PointRepository`에 별도로 `findByMemberIdForUpdate(memberId)`가 있다 — `@Lock(PESSIMISTIC_WRITE)` + `SELECT ... FOR UPDATE`. 회원마다 `Point`가 항상 1행 존재하는 게 정상 경로(아래 참고)이지만, 그 조회가 비었다고 곧바로 회원 미존재로 단정하지 않는다 — `MemberRepository#existsById`로 실제 회원 존재를 확인한다(회원은 있는데 `Point`만 없는 불변식 파손 상태를 실제 `MEMBER_NOT_FOUND`와 구분하기 위함, 자체 리뷰에서 발견).

## 규칙 / 검증
- 동시성 제어는 `POINT` 행 비관적 락으로 한다. 근거: `docs/policy/point.md`, [ADR-001](../../../adr/ADR-001-포인트-동시성제어.md).
- **`DataSeeder`가 회원을 시드할 때 `Point`(balance 0)도 함께 생성**하도록 이 이슈에서 바꿨다(`docs/dev/domain/entity/design.md`도 갱신). 최초 계획은 "#4 첫 충전 시 lazy 생성"이었으나, lazy 생성은 동일 회원이 첫 충전을 동시에 두 번 요청하면 두 트랜잭션이 동시에 `Point` insert를 시도해 `uk_member_id` unique 제약이 충돌하는 엣지케이스를 별도로 처리해야 한다. 시드 시점에 미리 만들어 두면 그 엣지케이스 자체가 없어지고, `PointService`는 "회원마다 Point가 항상 존재한다"는 단순한 가정으로 구현할 수 있다.
- **DataSeeder 시드는 완전히 빈 테이블일 때만 실행**(`memberRepository.count() == 0`)되므로, 이 변경 이전에 이미 존재하던 회원(또는 향후 DataSeeder를 거치지 않는 어떤 경로로 생성된 회원)은 `Point`가 없을 수 있다 — 이런 회원이 처음 충전을 시도하면 `PointService.charge`가 `memberRepository.existsById`로 실제 존재를 확인한 뒤 그 자리에서 `Point`를 만들어 정상 처리한다(자체 리뷰에서 발견 후 반영, `docs/logs/point/charge/001-charge.md` Attempt 2 참고).
- **(#34) 이 폴백 경로 자체의 동시성 구멍을 해소**: 이런 회원에게 첫 충전이 동시에 여러 번 들어오면(불변식이 깨진 회원 → 여러 트랜잭션이 동시에 `Point` 없음을 관찰) 다음 두 문제가 있었다 — ① MySQL 기본 격리수준(REPEATABLE READ)에서 존재하지 않는 행에 대한 `SELECT ... FOR UPDATE`가 갭 락을 걸어, 동시에 여러 트랜잭션이 같은 갭을 잠그려다 데드락이 남. ② insert 실패(uk_member_id 위반) 처리를 원래 트랜잭션 안에서 하면, MySQL이 duplicate-key 충돌 시 그 행에 공유 락을 잡아두는 것과 뒤이은 `SELECT ... FOR UPDATE`의 배타 락 승격이 맞물려 또 데드락이 남. 두 문제 모두 동시성 테스트(`PointServiceConcurrencyTest`)로 직접 재현해 확인했다.
  - **해결 (①)**: 처음엔 `charge`/`use` 트랜잭션을 `@Transactional(isolation = READ_COMMITTED)`로 낮춰 갭 락 자체를 없애려 했다. 하지만 자체 리뷰에서 이게 **order/create(#5)를 통한 실제 주문결제 경로에서는 무효화됨**을 발견했다 — `OrderService.create()`가 이미 자신의 트랜잭션(`@Transactional`, propagation 기본값 `REQUIRED`)을 연 상태에서 `pointService.use()`를 호출하는데, `use()`도 propagation을 지정하지 않아 `REQUIRED`이므로 **새 트랜잭션을 시작하지 않고 이미 열린 바깥 트랜잭션에 참여(join)**한다. Spring은 이미 활성 트랜잭션에 참여할 때 참여자 메서드의 `isolation` 속성을 적용하지 않고(`validateExistingTransaction` 기본값이 `false`라 예외도 없이 조용히 무시), 실제로는 `OrderService.create()`가 시작한 바깥 트랜잭션의 기본 격리(`REPEATABLE READ`)로 실행된다 — 즉 이 fix가 정작 가장 중요한 프로덕션 경로(주문 결제)에는 적용되지 않는, 호출자에 따라 있다가 없다가 하는 취약한 해결책이었다.
    최종적으로는 **격리수준에 의존하지 않는 방식**으로 바꿨다: `getLockedPoint`가 `PointRepository#existsByMemberId`(잠금 없는 일반 조회 — MVCC 스냅샷이라 어떤 격리수준에서도 락을 잡지 않는다)로 먼저 존재를 확인하고, 없을 때만 생성 경로를 타게 했다. 이러면 이 트랜잭션이 실제로 `SELECT ... FOR UPDATE`를 거는 시점엔 그 행이 항상 이미 존재해서(직접 있었거나 방금 만들어졌거나) 존재하지 않는 행에 대한 갭 락 자체가 걸릴 일이 없다 — 호출자가 자기 트랜잭션을 이미 열어뒀든 아니든, 격리수준이 무엇이든 상관없이 안전하다.
  - **해결 (②)**: `Point` 생성 자체는 `PointBootstrapService.ensurePointExists`(`REQUIRES_NEW`, 별도 트랜잭션)에 위임해, 그 트랜잭션이 커밋(또는 duplicate-key로 인한 롤백)까지 완전히 끝난 뒤에야 바깥 트랜잭션이 새로 `SELECT ... FOR UPDATE`를 걸도록 했다. `ensurePointExists`가 실패하면(동시에 다른 트랜잭션이 먼저 만듦) 대부분 `DataIntegrityViolationException`으로 곧바로 실패하지만, 커밋 타이밍이 겹쳐 락 대기가 길어지면 `CannotAcquireLockException`(락 대기 초과)으로 대신 나타날 수 있어 `PointService`가 둘 다 잡아 무시하고 재조회로 이어간다.
  - `REQUIRES_NEW`는 같은 스레드가 커넥션을 순간적으로 2개(바깥 보류분 + 중첩분) 쥐게 하므로, HikariCP `maximum-pool-size`를 10→25로 올렸다(`application.yml`, 최소 필요량 20에 여유분 +5) — 이 폴백 경로 자체가 드물어 실사용 부하는 낮지만, 동시 요청이 몰릴 때 풀 고갈을 피하기 위함.
  - 회귀 검증: `PointServiceConcurrencyTest`(직접 호출 경로)뿐 아니라 `OrderServiceConcurrencyTest`에 **Point 없는 회원으로 동시 주문**하는 케이스를 추가해, `OrderService.create()`가 이미 연 트랜잭션 안에서 호출되는 실제 경로에서도 데드락이 없는지 검증했다.
  - 알려진 트레이드오프 2가지(자체 리뷰에서 지적됨, 의도적으로 그대로 둠):
    - `Point` 생성이 별도 트랜잭션(`REQUIRES_NEW`)에서 독립적으로 커밋되므로, 생성 이후 같은 요청의 나머지 부분(잔액 갱신·이력 저장)이 실패해도(예: `amount`가 `Long.MAX_VALUE`에 가까워 `Math.addExact` 오버플로우) 이미 만든 `Point`(balance 0)는 롤백되지 않고 남는다. balance 0은 `DataSeeder`가 만드는 것과 동일한 유효한 초기 상태라 정합성 위반은 아니고, "생성 시도 자체가 요청 성공 여부와 완전히 묶이지 않는다"는 정도의 완화다.
    - `ensurePointExists`의 실패를 `uk_member_id` 위반으로 단정하고 잡는다(constraint 종류를 구분하지 않음) — 지금은 `points` 테이블에 다른 제약(FK 없음, [ADR-005](../../../adr/ADR-005-엔티티간-FK-ID-참조.md))이 없어 실질적으로 문제 없지만, 나중에 제약이 추가되면 다른 원인의 실패도 "이미 존재"로 오판할 수 있다.
- `PointChargeRequest`는 `@NotNull`로 null만 걸러내고(→ `COMMON_001`), 0 이하 검증은 Bean Validation이 아니라 서비스에서 직접 한다 — API 계약이 `POINT_001`이라는 도메인 전용 에러 코드를 요구하는데, Bean Validation 실패는 `GlobalExceptionHandler`에서 항상 `COMMON_001`로만 매핑되기 때문. `docs/api/point.md`의 에러 표에도 `COMMON_001`(null/누락)을 명시했다.
- 검증 레벨: Level 1(단위)·Level 2(컨트롤러 계약)·**Level 3(락·동시성 통합, `PointServiceConcurrencyTest` + `OrderServiceConcurrencyTest`)** PASS. 상세 근거는 `docs/logs/point/charge/001-charge.md`, `docs/logs/point/charge/002-concurrency-fix.md`.

## 관련 문서
- `docs/api/point.md` · `docs/policy/point.md` · [ADR-001](../../../adr/ADR-001-포인트-동시성제어.md)
- `docs/dev/domain/entity/design.md` (Point 시드 방식 변경 반영)
- 이슈 [#34](https://github.com/yxejxnn/coffee-order-system/issues/34) (이 폴백 경로 동시성 구멍 해소)
