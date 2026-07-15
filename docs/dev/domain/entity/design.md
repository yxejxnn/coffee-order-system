# domain/entity — Design

## 개요
회원(`Member`)·포인트(`Point`)·포인트 이력(`PointHistory`)·메뉴(`Menu`)·주문(`Order`) 5개 JPA 엔티티와 기본 `JpaRepository`, 초기 시드 데이터(회원 3명·메뉴 5개)를 제공한다. 엔티티/리포지토리 계층만 다루며, 이후 모든 기능(#3~#8)이 이 위에서 구현된다.

## 패키지 구조
도메인 우선 패키징: `com.coffeeorder.domain.{도메인}.{entity,repository}`. 도메인 4개 — `member`·`point`(+`PointHistory`, 잔액과 감사이력을 한 도메인으로 묶음)·`menu`·`order`(엔티티 `Order`). 여러 도메인을 다루는 `DataSeeder`(`ApplicationRunner`)는 도메인 패키지 밖 `com.coffeeorder.config`에 위치. 상세 규칙: `docs/code-convention.md`.

## API / 인터페이스
이 기능 자체는 HTTP 엔드포인트를 노출하지 않는다(엔티티/리포지토리 계층만).

## 데이터 모델
- 테이블: `members`·`points`·`point_histories`·`menus`·`orders`. 상세 스펙은 `docs/db/*.md`, 전체 그림은 `docs/db/erd.md`.
- 엔티티 생성자: no-arg는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`(Lombok), 필드(비즈니스) 생성자는 직접 작성. `@Getter`도 Lombok.
- 다른 도메인 엔티티 참조는 `@ManyToOne`/`@OneToOne` 객체가 아니라 **FK id(`Long`)만 보유**한다 — `Point.memberId`, `PointHistory.memberId`, `Order.memberId`/`Order.menuId`. 판단 기준은 `docs/code-convention.md`, 이번 결정의 근거는 [ADR-005](../../../adr/ADR-005-엔티티간-FK-ID-참조.md).

## 규칙 / 검증
- `Point`는 `Member`와 1:1(`uk_member_id` unique), 충전/차감 락 범위를 잔액 행 하나로 최소화하기 위해 분리됐다. → [ADR-004](../../../adr/ADR-004-데이터모델-포인트분리.md) · [ADR-001](../../../adr/ADR-001-포인트-동시성제어.md)
- `PointHistory.type`은 enum(`CHARGE`/`USE`)이며 `@JdbcTypeCode(SqlTypes.VARCHAR)`로 `varchar(10)`을 강제한다(Hibernate 7이 MySQL에서 기본적으로 native `enum(...)`을 생성하는 것을 막기 위함).
- `Order.orderGroupId`(UUID)는 unique — 결제 단위·Kafka 멱등 키·재조회 키를 겸한다. `idx_created_menu(created_at, menu_id)`는 최근 7일 인기 메뉴 재집계 쿼리용. → [ADR-004](../../../adr/ADR-004-데이터모델-포인트분리.md)
- `DataSeeder`가 앱 기동 시 회원 3명·메뉴 5개를 각 테이블이 비어 있을 때만 시드한다(idempotent). `Point`/`PointHistory`/`Order`는 시드 대상이 아니다(이슈 스코프가 "회원·메뉴"로 명시, `Point`는 #4 첫 충전에서 lazy 생성).
- FK id만 보유하는 설계상 **DB 레벨 FK 제약은 자동 생성되지 않는다** — 참조 무결성은 서비스 레이어의 존재 검증(`MEMBER_NOT_FOUND`/`MENU_NOT_FOUND`, #4·#5에서 구현)에 의존한다. → [ADR-005](../../../adr/ADR-005-엔티티간-FK-ID-참조.md)

## 관련 문서
- `docs/db/erd.md` + `docs/db/{member,point,point-history,menu,orders}.md`
- [ADR-001](../../../adr/ADR-001-포인트-동시성제어.md) · [ADR-004](../../../adr/ADR-004-데이터모델-포인트분리.md) · [ADR-005](../../../adr/ADR-005-엔티티간-FK-ID-참조.md)
- `docs/code-convention.md` (패키지 구조·엔티티 생성자·FK 참조 판단 기준)
