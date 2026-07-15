# ADR-005 엔티티 간 참조 — 연관관계 객체 대신 FK id 보유

## 상태와 결정일
Accepted. 결정일: 2026-07-15.

## 맥락과 문제
`Point`·`PointHistory`·`Order`는 모두 다른 도메인(`member`, `menu`)의 엔티티를 참조해야 한다. JPA에서 이걸 표현하는 방법은 두 가지다.
1. `@ManyToOne`/`@OneToOne` + `@JoinColumn`으로 상대 엔티티 객체를 필드로 보유.
2. 상대 엔티티 없이 FK 컬럼값(`Long memberId`, `Long menuId`)만 필드로 보유.

원래는 (1)로 구현했으나(엔티티 간 연관관계 매핑), "연관관계 객체를 실제로 가지고 있을 필요가 있는가"라는 질문에 다시 검토했다.

## 결정 동인
- **도메인 패키지 분리와의 정합성** — 이 프로젝트는 `com.coffeeorder.domain.{member,point,menu,order}`로 도메인 우선 패키징을 하기로 했다(`docs/code-convention.md`). `@ManyToOne` 객체 참조를 쓰면 `order` 패키지가 `member`·`menu` 패키지의 엔티티 클래스를 import해야 해서, 패키지는 분리했지만 엔티티 계층에서부터 도메인 간 컴파일 의존이 다시 생긴다.
- **실제 API 스펙에 객체 그래프 탐색이 필요한지 확인** — `docs/api/order.md`·`point.md`·`ranking.md`의 응답 스펙을 모두 확인한 결과, 모든 응답 필드가 `memberId`/`menuId`(raw id)이거나 별도 리포지토리 조회로 채우는 값(`ranking`의 `menu.name`)이며, "주문 엔티티에서 회원/메뉴 객체를 바로 참조해 필드를 꺼내 쓰는" 응답은 하나도 없다.
- **N+1/LazyInitializationException 리스크 회피** — 자체 리뷰(PR #17)에서 이미 LAZY 연관관계의 N+1 위험이 지적된 바 있다.

## 검토한 선택지

| 선택지 | 도메인 결합 | N+1/Lazy 리스크 | DB 참조 무결성 | 판단 |
| --- | --- | --- | --- | --- |
| `@ManyToOne`/`@OneToOne` 객체 참조 | `order`가 `member`·`menu` 엔티티 클래스에 컴파일 의존 | LAZY 프록시·N+1 위험 있음 | Hibernate가 FK 제약을 DDL에 자동 생성 | 제외 |
| **FK id(`Long`)만 보유** | 도메인 간 엔티티 의존 없음(id 값만 공유) | 없음(프록시 자체가 없음) | DB FK 제약 없음 — 애플리케이션(서비스) 검증에만 의존 | **채택** |

## 결정과 이유
`Point.memberId`, `PointHistory.memberId`, `Order.memberId`/`Order.menuId`를 전부 `Long` 컬럼으로 바꾸고 `@ManyToOne`/`@OneToOne`/`@JoinColumn`을 제거했다.
- 계획된 API 응답 어디에도 연관 엔티티 필드를 직접 노출하는 곳이 없어 객체 그래프 탐색이 불필요하다.
- 이미 서비스 레이어에서 회원/메뉴 **존재 검증**을 하기로 되어 있다(`ErrorCode.MEMBER_NOT_FOUND`/`MENU_NOT_FOUND`, `docs/api/order.md`). DB FK 제약은 이 검증을 대체하는 유일한 수단이 아니었다.
- 도메인 패키지 분리 목적(각 도메인을 독립적으로 다루기)과 일관되게, 엔티티 계층에서도 도메인 간 결합을 없앤다.

## 결과와 단점
- 얻는 것: 도메인 간 컴파일 의존 제거(각 엔티티가 자기 도메인 패키지만으로 완결), LAZY 로딩/N+1 리스크 원천 차단, 각 도메인을 독립적으로 영속화·테스트 가능.
- 비용: **DB 레벨 FK 제약이 사라진다.** 참조 무결성은 전적으로 애플리케이션(서비스 레이어의 존재 확인)에 의존하게 되어, 버그나 수동 SQL로 잘못된 `member_id`/`menu_id`가 들어가도 DB가 막지 못한다.

## 검증 현황과 계획
- Level 3(실제 MySQL) 확인 완료 — FK 제약 없이도 `uk_member_id`(points)·`uk_order_group_id`(orders) unique 제약과 `idx_created_menu` 인덱스는 정상 생성·동작함(`EntityMappingTest`).
- 회원/메뉴 존재 검증(서비스 레이어)은 #4(point/charge)·#5(order/create)에서 구현 예정.

## 재검토 조건
- 나중에 "주문 목록에 회원 이름을 같이 보여달라" 같은, 연관 엔티티 필드를 실제로 join해서 한 번에 가져와야 하는 요구가 생기면: 먼저 리포지토리에 fetch join 전용 조회 쿼리(DTO 프로젝션)를 추가하는 방향을 검토하고, 그래도 부족하면 그 지점에서만 연관관계 객체 참조 재도입을 검토한다.

## 관련 항목
- [db/point](../db/point.md) · [db/point-history](../db/point-history.md) · [db/orders](../db/orders.md)
- [ADR-004](ADR-004-데이터모델-포인트분리.md) (POINT 1:1 분리, order_group_id)
- `docs/code-convention.md` (도메인 우선 패키징)
