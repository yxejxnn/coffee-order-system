# members (회원)

엔티티 `Member`. 주문·포인트의 주체. 발제 범위상 **회원가입 API는 없고 시드 데이터로 존재**한다고 가정한다(사용자 식별값 = `members.id`). → [open-questions](../open-questions.md)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 사용자 식별값 |
| name | VARCHAR(50) | NOT NULL | 회원 이름 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

## 인덱스
- PK(id) 외 없음.

## 관계
- `POINT` 1:1 (회원 1명 = 포인트 잔액 1행).
- `POINT_HISTORY` 1:N, `ORDER` 1:N.

## 사용하는 기능
- point/charge, order/create (사용자 식별·검증).
