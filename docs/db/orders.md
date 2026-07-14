# orders (주문/결제 내역)

결제 완료된 주문 1건 = 1행. 인기 메뉴 카운트의 **원천(SSOT)**이며, 주문 완료 이벤트의 근거 데이터.
(`orders`는 SQL 예약어 `order` 회피를 위한 관용 복수 명명.)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| member_id | BIGINT | FK(member.id), NOT NULL | 주문 회원 |
| menu_id | BIGINT | FK(menu.id), NOT NULL | 주문 메뉴 |
| quantity | INT | NOT NULL, default 1 | 수량 |
| unit_price | INT | NOT NULL | 주문 시점 단가 스냅샷 |
| total_price | BIGINT | NOT NULL | `unit_price * quantity` (차감액) |
| order_group_id | VARCHAR(36) | NOT NULL, UNIQUE | 결제/이벤트 식별자(UUID). Kafka 멱등 키 |
| created_at | DATETIME | NOT NULL | 주문 시각(= 인기 집계 기준 시각) |

## 인덱스
- uk_order_group_id (order_group_id) — UNIQUE, 멱등/재조회 키.
- idx_created_menu (created_at, menu_id) — 최근 7일 인기 메뉴 재집계 쿼리용.

## 관계
- `MEMBER` 1:N, `MENU` 1:N.

## 규칙
- 주문 행 저장 + 포인트 차감 + `POINT_HISTORY(USE)` 기록은 **하나의 트랜잭션**. → [ADR-001](../adr/ADR-001-포인트-동시성제어.md)
- 트랜잭션 커밋 후 `order_group_id`를 담은 이벤트를 발행한다. → [ADR-002](../adr/ADR-002-주문이벤트-비동기전달.md)
- `ORDER_ITEM` 없이 메뉴 1건 단위. 다건 확장 시 `order_group_id`로 묶는다. → [ADR-004](../adr/ADR-004-데이터모델-포인트분리.md)

## 사용하는 기능
- order/create, ranking/popular (재집계·검증), 데이터 수집 플랫폼 전송.
