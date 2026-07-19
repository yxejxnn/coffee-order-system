# menus (커피 메뉴)

엔티티 `Menu`. 주문 대상 커피 메뉴. 발제 범위상 **재고 개념은 없다**(무제한 판매 가정). → [open-questions](../open-questions.md)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 메뉴 ID |
| name | VARCHAR(100) | NOT NULL | 메뉴 이름 |
| price | INT | NOT NULL | 가격(원, 1원=1P) |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

## 인덱스
- PK(id) 외 없음.

## 관계
- `ORDER` 1:N.

## 규칙
- 주문 시 `menus.price`를 `orders.unit_price`로 **스냅샷**한다. 이후 가격 변경이 과거 주문에 영향 없음.

## 사용하는 기능
- menu/list (목록 조회), order/create (가격 스냅샷), ranking/popular (이름·가격 표시).
