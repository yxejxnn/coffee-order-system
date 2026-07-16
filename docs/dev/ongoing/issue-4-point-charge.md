# 포인트 충전 API (+동시성 락)

대상: point/charge
이슈: [#4](https://github.com/yxejxnn/coffee-order-system/issues/4)

## 배경 / 요구
발제 2번 — 포인트 충전. **락 리포지토리 패턴을 이 이슈에서 확정**(#5가 재사용).

## 설계 (HOW)
- `PointController` → `PointService.charge(memberId, amount)` → `PointRepository.findByMemberIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`, **#5가 재사용할 락 리포지토리 패턴**).
- 검증 순서: `amount <= 0` → `INVALID_AMOUNT`(POINT_001) 먼저, 그다음 `POINT` 락 조회 실패 → `MEMBER_NOT_FOUND`(MEMBER_001).
  - 별도로 `memberRepository.existsById`를 조회하지 않는다 — `POINT`는 `MEMBER` 1:1이며 시드에서 항상 함께 생성되므로(아래 시드 변경), 락 조회 실패 = 회원 미존재로 취급해도 안전하고 쿼리도 하나로 준다.
- `Point.charge(amount)`로 잔액을 증가시키고(엔티티 메서드), 같은 트랜잭션에서 `PointHistory(CHARGE)`를 저장.
- **시드 방식 변경(사용자 확인 완료, `docs/dev/domain/entity/design.md` 갱신)**: 원래 계획("Point는 #4 첫 충전에서 lazy 생성")과 다르게, 이번 이슈에서 `DataSeeder`가 회원 시드 시 `Point(memberId)`(balance 0)도 함께 저장하도록 바꿨다. lazy 생성은 동일 회원의 첫 충전이 동시에 두 번 들어오면 두 트랜잭션이 동시에 `Point` insert를 시도해 unique 제약 충돌을 별도로 처리해야 하는데, 시드로 미리 만들어두면 그 동시성 엣지케이스 자체가 없어지고 서비스 로직도 단순해진다.
- 동시성 검증(Level 3): `PointServiceConcurrencyTest` — 같은 회원에 N스레드 동시 충전 후 최종 잔액 = N × amount, `PointHistory` 건수 = N을 확인(lost update 없음).

## 관련 결정·질문
- [`docs/api/point.md`](../../api/point.md) · [`docs/policy/point.md`](../../policy/point.md)
- [`ADR-001`](../../adr/ADR-001-포인트-동시성제어.md) (비관적 락)

## 태스크
- [x] `DataSeeder`: 회원 시드 시 `Point` 행도 함께 생성 (lazy 생성 계획에서 변경)
- [x] `PointRepository.findByMemberIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) — 락 리포지토리 패턴
- [x] `Point.charge(amount)` 엔티티 메서드
- [x] `PointChargeRequest`/`PointChargeResponse` DTO
- [x] `PointService.charge` — 검증 + 락 + `PointHistory(CHARGE)` 기록
- [x] `PointController` `POST /api/points/charge`
- [x] 단위 테스트(Service/Controller) + `DataSeederTest` 갱신
- [x] 동시 충전 N스레드 통합 테스트(Level 3)

## 평가(통과) 기준
- 정상 충전 + 잔액, `INVALID_AMOUNT`/`MEMBER_NOT_FOUND`.
- **동시 충전 N스레드 테스트로 lost update 0** — **Level 4**.
