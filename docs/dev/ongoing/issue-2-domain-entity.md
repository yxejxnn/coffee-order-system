# 도메인 엔티티 + 스키마 + 시드

대상: domain/entity
이슈: [#2](https://github.com/yxejxnn/coffee-order-system/issues/2)

## 배경 / 요구
5개 엔티티와 초기 데이터를 잡아 이후 기능 이슈의 기반을 만든다.

## 설계 (HOW)
- **패키지 구조를 도메인 우선으로 재구성**(`com.coffeeorder.domain.{도메인}.{계층}`, `docs/code-convention.md` 참고): 계층 우선(`entity`/`repository` 패키지에 전 도메인이 섞이는 구조)에서 도메인 우선으로 전환. 도메인 4개: `member`·`point`(+`PointHistory`)·`menu`·`order`.
- 엔티티 5개: `domain.member.entity.Member`·`domain.point.entity.Point`(1:1, `@JoinColumn(member_id)` unique)·`domain.point.entity.PointHistory`(enum `PointHistoryType` STRING 매핑, `@JdbcTypeCode(SqlTypes.VARCHAR)`로 MySQL 네이티브 enum 대신 varchar(10) 강제)·`domain.menu.entity.Menu`·`domain.order.entity.Order`(`order_group_id` unique + `idx_created_menu(created_at, menu_id)`). no-arg 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`(Lombok), 필드 생성자는 직접 작성, `@Getter`도 Lombok. `@Table(name=...)`은 전부 복수형(`members`/`points`/`point_histories`/`menus`/`orders`).
- 리포지토리 5개: 각 도메인 패키지의 `repository` 하위에 `JpaRepository` 인터페이스만 (커스텀 쿼리 없음, 필요 이슈에서 추가). `Order` → `OrderRepository`(엔티티명과 동일 패턴).
- `com.coffeeorder.config.DataSeeder`(`ApplicationRunner`, 도메인 밖 — member+menu를 함께 다루는 cross-domain 컴포넌트): 테이블이 비어 있을 때만 회원 3명·메뉴 5개 시드(재기동 시 중복 삽입 방지). Point/PointHistory/Order는 시드 범위 밖(이슈 스코프가 "회원·메뉴"로 명시).
- 테스트 의존성: Spring Boot 4.1.0에서 `@DataJpaTest`/`AutoConfigureTestDatabase`가 `spring-boot-test-autoconfigure`에서 분리되어 `spring-boot-starter-data-jpa-test`(신규 패키지 `org.springframework.boot.data.jpa.test.autoconfigure` / `org.springframework.boot.jdbc.test.autoconfigure`)로 이동함 — `build.gradle`에 추가.

## 관련 결정·질문
- `docs/db/erd.md` + 각 테이블 md (스키마 원천)
- [`ADR-004`](../../adr/ADR-004-데이터모델-포인트분리.md) (POINT 1:1 분리, order_group_id, no ORDER_ITEM)

## 태스크
- [x] 엔티티 5개 작성 (생성자 직접 작성)
- [x] JpaRepository 5개 작성
- [x] 회원·메뉴 시드 데이터 (`DataSeeder`)
- [x] `@DataJpaTest` 통합 테스트 (1:1 unique, FK 매핑, order_group_id unique) — 실제 MySQL(docker) 대상
- [x] 로컬 기동 후 스키마·시드 실제 확인

## 평가(통과) 기준
- 엔티티 ↔ `docs/db/` 명세 일치, 스키마 생성/기동 — **Level 5**.
- 1:1(Point)·FK 매핑 검증 테스트.
