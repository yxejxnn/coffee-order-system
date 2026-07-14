# order API

커피 주문/결제. (발제 요구사항 3) — 포인트 차감 + 주문내역 데이터 수집 플랫폼 실시간 전송.

## POST /api/orders — 커피 주문/결제
- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | memberId | number | Y | 사용자 식별값 |
  | menuId | number | Y | 주문 메뉴 ID |
  | quantity | number | N | 수량(기본 1, 양의 정수) |
- 응답: `201 Created`
  ```json
  {
    "code": "SUCCESS",
    "data": {
      "orderGroupId": "b1f2...-uuid",
      "memberId": 1,
      "menuId": 2,
      "quantity": 1,
      "totalPrice": 4500,
      "balance": 10500
    }
  }
  ```
  | 필드 | 타입 | 설명 |
  |------|------|------|
  | orderGroupId | string | 결제 식별자(UUID) |
  | totalPrice | number | 차감된 금액 |
  | balance | number | 결제 후 잔액 |
- 에러:
  | 코드 | 상태 | 조건 |
  |------|------|------|
  | MEMBER_001 (MEMBER_NOT_FOUND) | 404 | 존재하지 않는 회원 |
  | MENU_001 (MENU_NOT_FOUND) | 404 | 존재하지 않는 메뉴 |
  | ORDER_001 (INVALID_QUANTITY) | 400 | quantity ≤ 0 |
  | POINT_002 (INSUFFICIENT_POINT) | 409 | 잔액 < 결제금액 |

## 규칙 · 동시성 · 이벤트
- 처리 순서(하나의 트랜잭션): 회원·메뉴 검증 → `POINT` 행 **비관적 락** → 잔액 확인·차감 → `ORDERS` 저장 → `POINT_HISTORY(USE)` 기록. → [ADR-001](../adr/ADR-001-포인트-동시성제어.md)
- `unit_price`는 주문 시점 `menu.price` 스냅샷, `total_price = unit_price * quantity`.
- **트랜잭션 커밋 후** `OrderCompletedEvent`(`order_group_id` 포함) 발행 → 두 소비자가 처리:
  (a) 데이터 수집 플랫폼 전송, (b) 인기 메뉴 Redis 카운트 증가. → [ADR-002](../adr/ADR-002-주문이벤트-비동기전달.md) · [ADR-003](../adr/ADR-003-인기메뉴-집계전략.md)
- 데이터 수집 플랫폼은 Mock으로 구현하고 `memberId, menuId, 결제금액`을 전송(발제 3).
