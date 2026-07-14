# 커피 주문/결제 API (+락·트랜잭션 +이벤트 발행)

대상: order/create
이슈: [#5](https://github.com/yxejxnn/coffee-order-system/issues/5)

## 배경 / 요구
발제 3번 — 주문/결제 + 커밋 후 이벤트 발행. **Kafka producer/공통 설정을 이 이슈에서 확정**(#6·#7이 재사용).

## 설계 (HOW)
> 집을 때 작성.
- `POST /api/orders`: 검증 → `POINT` 비관적 락(#4 재사용) → 차감 → `ORDERS` 저장(단가 스냅샷·order_group_id) → `POINT_HISTORY(USE)`, 단일 트랜잭션.
- 커밋 후 `@TransactionalEventListener(AFTER_COMMIT)` → `OrderCompletedEvent` Kafka 발행.

## 관련 결정·질문
- [`docs/api/order.md`](../../api/order.md)
- [`ADR-001`](../../adr/ADR-001-포인트-동시성제어.md) · [`ADR-002`](../../adr/ADR-002-주문이벤트-비동기전달.md) · [`ADR-004`](../../adr/ADR-004-데이터모델-포인트분리.md)

## 태스크
- [ ] (집을 때 세분화)

## 평가(통과) 기준
- 201 + 주문/잔액, `INSUFFICIENT_POINT`(409, 차감 없음)·`MENU_NOT_FOUND`.
- **동시 주문 N스레드 테스트로 초과 차감 0·주문 수 정확** — **Level 4**.
- 커밋된 주문만 발행(롤백 시 미발행)·토픽 적재 — **Level 5**, 실제 HTTP **Level 6**.
