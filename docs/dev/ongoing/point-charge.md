# 포인트 충전 API (+동시성 락)

대상: point/charge
이슈: [#4](https://github.com/yxejxnn/coffee-order-system/issues/4)

## 배경 / 요구
발제 2번 — 포인트 충전. **락 리포지토리 패턴을 이 이슈에서 확정**(#5가 재사용).

## 설계 (HOW)
> 집을 때 작성.
- `POST /api/points/charge`, `POINT` 행 비관적 락으로 잔액 증가 + `POINT_HISTORY(CHARGE)` 동일 트랜잭션.

## 관련 결정·질문
- [`docs/api/point.md`](../../api/point.md) · [`docs/policy/point.md`](../../policy/point.md)
- [`ADR-001`](../../adr/ADR-001-포인트-동시성제어.md) (비관적 락)

## 태스크
- [ ] (집을 때 세분화)

## 평가(통과) 기준
- 정상 충전 + 잔액, `INVALID_AMOUNT`/`MEMBER_NOT_FOUND`.
- **동시 충전 N스레드 테스트로 lost update 0** — **Level 4**.
