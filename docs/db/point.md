# points (포인트 잔액)

엔티티 `Point`. 회원의 **현재 포인트 잔액**. 충전·차감의 동시성 제어가 걸리는 핵심 행.
`MEMBER`에서 1:1로 분리해 **락 범위를 잔액 행 하나로 최소화**한다. → [ADR-004](../adr/ADR-004-데이터모델-포인트분리.md)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| member_id | BIGINT | FK(members.id)\*, NOT NULL, UNIQUE | 회원 (1:1) |
| balance | BIGINT | NOT NULL, default 0 | 현재 잔액(P). 항상 ≥ 0 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

## 인덱스
- uk_member_id (member_id) — UNIQUE, 1:1 보장 + 조회 키.

\* `member_id`는 엔티티 연관관계(`@ManyToOne`) 없이 **FK id(`Long`)만 보유** — DB FK 제약은 없고 서비스 레이어 존재 검증에 의존한다. → [ADR-005](../adr/ADR-005-엔티티간-FK-ID-참조.md)

## 관계
- `MEMBER` 1:1.

## 동시성
- 충전/차감은 이 행을 **비관적 락(`SELECT … FOR UPDATE`)**으로 잠그고 읽고 쓴다. → [ADR-001](../adr/ADR-001-포인트-동시성제어.md)
- `balance`가 잔액의 **유일한 원천**. `POINT_HISTORY` 합산으로 잔액을 계산하지 않는다.

## 사용하는 기능
- point/charge (증가), order/create (차감).
