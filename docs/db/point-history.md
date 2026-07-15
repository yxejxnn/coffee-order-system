# point_histories (포인트 이력)

엔티티 `PointHistory`. 충전·사용 내역의 **append-only 감사 로그**. 잔액 계산의 원천이 아니다(원천은 `POINT.balance`).
"언제 얼마가 왜 움직였나"의 추적·검증용.

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| member_id | BIGINT | FK(members.id), NOT NULL | 회원 |
| type | VARCHAR(10) | NOT NULL | `CHARGE`(충전) \| `USE`(주문 결제) |
| amount | BIGINT | NOT NULL | 증감액(양수). 방향은 `type`이 결정 |
| order_group_id | VARCHAR(36) | NULL | `USE`일 때 결제 식별자(어떤 주문의 차감인지) |
| created_at | DATETIME | NOT NULL | 발생 시각 |

## 인덱스
- idx_member_created (member_id, created_at) — 회원별 이력 조회.

## 관계
- `MEMBER` 1:N.

## 규칙
- **삽입만 한다(수정/삭제 없음)**. 잔액 변경과 **같은 트랜잭션**에서 기록한다.

## 사용하는 기능
- point/charge, order/create.
