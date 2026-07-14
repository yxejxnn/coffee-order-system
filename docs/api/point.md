# point API

포인트 충전. (발제 요구사항 2) — 결제는 포인트로만, 1원 = 1P.

## POST /api/points/charge — 포인트 충전
- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | memberId | number | Y | 사용자 식별값 |
  | amount | number | Y | 충전 금액(원). 양의 정수만 |
- 응답: `200 OK`
  ```json
  { "code": "SUCCESS", "data": { "memberId": 1, "balance": 15000 } }
  ```
  | 필드 | 타입 | 설명 |
  |------|------|------|
  | memberId | number | 회원 ID |
  | balance | number | 충전 후 잔액(P) |
- 에러:
  | 코드 | 상태 | 조건 |
  |------|------|------|
  | INVALID_AMOUNT | 400 | amount ≤ 0 |
  | MEMBER_NOT_FOUND | 404 | 존재하지 않는 회원 |

## 규칙 · 동시성
- 검증은 최소(양수만, 상한 없음). → [policy/point](../policy/point.md)
- 동일 회원 동시 충전 시 잔액 정합성은 **`POINT` 행 비관적 락**으로 보장. → [ADR-001](../adr/ADR-001-포인트-동시성제어.md)
- 잔액 변경과 `POINT_HISTORY(CHARGE)` 기록은 같은 트랜잭션.
