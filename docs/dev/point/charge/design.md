# point/charge — Design

## 개요
회원의 포인트 잔액을 충전한다. 동일 회원에 대한 동시 충전 요청에서도 잔액 정합성(lost update 없음)을 보장하며, 이 이슈에서 확정한 비관적 락 리포지토리 패턴을 order/create(#5)가 그대로 재사용한다.

## API / 인터페이스
- `POST /api/points/charge` — 상세 계약(요청/응답/에러 코드)은 `docs/api/point.md` 참조.
- 구현: `domain/point/controller/PointController` → `domain/point/service/PointService#charge` → `domain/point/repository/PointRepository#findByMemberIdForUpdate`(`@Lock(PESSIMISTIC_WRITE)`) → `domain/point/entity/Point#charge` + `domain/point/repository/PointHistoryRepository`.
- 검증 순서: `amount <= 0`(또는 null, 단 null은 컨트롤러 `@Valid`의 `@NotNull`이 먼저 가로채 `COMMON_001`로 응답) → `INVALID_AMOUNT`(400) 먼저 확인 후, `POINT` 락 조회 → 없으면 `MemberRepository#existsById`로 회원 존재를 확인해 없으면 `MEMBER_NOT_FOUND`(404), 있으면(불변식이 깨진 예외 상황) 그 자리에서 `Point`를 만들어 계속 진행.

## 데이터 모델
- `points.balance`를 증가시키고(`Math.addExact`로 오버플로우 시 예외 — 조용한 wrap 방지), 같은 트랜잭션에서 `point_histories`에 `CHARGE` 이력 1건을 남긴다(`order_group_id`는 null). 상세 스펙: `docs/db/point.md`, `docs/db/point-history.md`.
- `PointRepository`에 별도로 `findByMemberIdForUpdate(memberId)`가 있다 — `@Lock(PESSIMISTIC_WRITE)` + `SELECT ... FOR UPDATE`. 회원마다 `Point`가 항상 1행 존재하는 게 정상 경로(아래 참고)이지만, 그 조회가 비었다고 곧바로 회원 미존재로 단정하지 않는다 — `MemberRepository#existsById`로 실제 회원 존재를 확인한다(회원은 있는데 `Point`만 없는 불변식 파손 상태를 실제 `MEMBER_NOT_FOUND`와 구분하기 위함, 자체 리뷰에서 발견).

## 규칙 / 검증
- 동시성 제어는 `POINT` 행 비관적 락으로 한다. 근거: `docs/policy/point.md`, [ADR-001](../../../adr/ADR-001-포인트-동시성제어.md).
- **`DataSeeder`가 회원을 시드할 때 `Point`(balance 0)도 함께 생성**하도록 이 이슈에서 바꿨다(`docs/dev/domain/entity/design.md`도 갱신). 최초 계획은 "#4 첫 충전 시 lazy 생성"이었으나, lazy 생성은 동일 회원이 첫 충전을 동시에 두 번 요청하면 두 트랜잭션이 동시에 `Point` insert를 시도해 `uk_member_id` unique 제약이 충돌하는 엣지케이스를 별도로 처리해야 한다. 시드 시점에 미리 만들어 두면 그 엣지케이스 자체가 없어지고, `PointService`는 "회원마다 Point가 항상 존재한다"는 단순한 가정으로 구현할 수 있다.
- **DataSeeder 시드는 완전히 빈 테이블일 때만 실행**(`memberRepository.count() == 0`)되므로, 이 변경 이전에 이미 존재하던 회원(또는 향후 DataSeeder를 거치지 않는 어떤 경로로 생성된 회원)은 `Point`가 없을 수 있다 — 이런 회원이 처음 충전을 시도하면 `PointService.charge`가 `memberRepository.existsById`로 실제 존재를 확인한 뒤 그 자리에서 `Point`를 만들어 정상 처리한다(자체 리뷰에서 발견 후 반영, `docs/logs/point/charge/001-charge.md` Attempt 2 참고). 단, 이 폴백 경로 자체가 동시에 두 번 호출되는 아주 좁은 경우(불변식이 이미 깨진 회원의 첫 충전이 다시 동시에 두 번 들어오는 경우)엔 여전히 `uk_member_id` 충돌 가능성이 이론적으로 남아 있다 — 정상 경로(시드로 생성된 회원)에서는 발생하지 않는다.
- `PointChargeRequest`는 `@NotNull`로 null만 걸러내고(→ `COMMON_001`), 0 이하 검증은 Bean Validation이 아니라 서비스에서 직접 한다 — API 계약이 `POINT_001`이라는 도메인 전용 에러 코드를 요구하는데, Bean Validation 실패는 `GlobalExceptionHandler`에서 항상 `COMMON_001`로만 매핑되기 때문. `docs/api/point.md`의 에러 표에도 `COMMON_001`(null/누락)을 명시했다.
- 검증 레벨: Level 1(단위)·Level 2(컨트롤러 계약)·**Level 3(락·동시성 통합, `PointServiceConcurrencyTest`)** PASS. 상세 근거는 `docs/logs/point/charge/001-charge.md`.

## 관련 문서
- `docs/api/point.md` · `docs/policy/point.md` · [ADR-001](../../../adr/ADR-001-포인트-동시성제어.md)
- `docs/dev/domain/entity/design.md` (Point 시드 방식 변경 반영)
