# 001-entity — 도메인 엔티티 + 스키마 + 시드 (로그)

## Attempt 1 — 2026-07-15  ✅ PASS

- 시도: `docs/db/*.md` 명세대로 `Member`·`Point`·`PointHistory`·`Menu`·`Orders` JPA 엔티티(생성자 직접 작성) + 5개 `JpaRepository` + 회원 3명·메뉴 5개를 넣는 `DataSeeder`(`ApplicationRunner`, count==0일 때만 삽입) 작성. `@DataJpaTest`로 1:1(Point unique)·FK 매핑·`order_group_id` unique 제약을 검증하는 통합 테스트 작성.
- 이슈: Spring Boot 4.1.0에서 `@DataJpaTest`/`AutoConfigureTestDatabase`가 `spring-boot-test-autoconfigure`에서 빠져 컴파일 실패 → `spring-boot-starter-data-jpa-test` 의존성 추가로 해결(신규 패키지: `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`).
- 이슈: 로컬 기동 후 스키마 확인 중 `point_history.type`이 문서 스펙(`VARCHAR(10)`)과 다르게 MySQL 네이티브 `enum(...)`으로 생성됨(Hibernate 7의 `@Enumerated(STRING)` 기본 동작이 MySQL 등 enum 지원 dialect에서 네이티브 enum을 우선 사용) → `@JdbcTypeCode(SqlTypes.VARCHAR)` 추가로 `varchar(10) NOT NULL` 강제, 재확인 완료.
- 결과: `./gradlew build` 전체 통과(신규 테스트 5개 포함). 로컬 `docker compose up -d mysql` 대상으로 실제 스키마 생성 확인 — 5개 테이블(`member`, `point`, `point_history`, `menu`, `orders`) 모두 명세와 일치(컬럼·타입·PK/FK·unique·index).
- 검증 레벨: **Level 1(빌드+단위/통합테스트) PASS** · **Level 3(DB·JPA 통합, 실제 MySQL) PASS** · **Level 5(로컬 기동) PASS**.
- 증거:
  - 테스트: `EntityMappingTest`(3) + `DataSeederTest`(2) 전부 PASS, `./gradlew build` → `BUILD SUCCESSFUL`.
  - DDL 샘플(`SHOW CREATE TABLE`): `point` — `UNIQUE KEY uk_member_id (member_id)` + FK; `orders` — `UNIQUE KEY uk_order_group_id (order_group_id)` + `KEY idx_created_menu (created_at,menu_id)`; `point_history` — `type varchar(10) NOT NULL` (+ Hibernate가 부가한 CHECK 제약, 문서 스펙과 상충 없음).
  - 시드 확인: `member`에 홍길동/김민준/이서연 3행, `menu`에 아메리카노 4500 등 5행 정상 삽입(앱 재기동 시 중복 삽입 안 됨은 `DataSeederTest`로 검증).
- 완료 후 `docker compose down`으로 로컬 상태 원복.

## Attempt 2 — 2026-07-15  ✅ PASS (자체 리뷰 반영)

- 시도: PR #17에 `/code-review --comment`로 자체 리뷰(8개 발견사항 인라인 코멘트) 진행 후 반영.
  - 수정: `OrderRepository` → `OrdersRepository`(다른 리포지토리 네이밍 패턴 통일), `point_history.type` 실제 컬럼 타입(varchar)을 고정하는 회귀 테스트 추가, `code-convention.md`에 엔티티 enum 매핑 규칙 명문화, `AGENTS.md`에 테스트 실행 시 실제 MySQL 필요하다는 안내 추가.
  - 보류(사유 기재 후 PR 코멘트로 회신): `DataSeeder`의 check-then-act 레이스·`@Profile` 가드 부재(로컬 개발 스코프상 과설계로 판단), `Orders.totalPrice` 파생 검증 부재(서비스 레이어 책임, #5에서 처리 예정), 5개 엔티티의 `@MappedSuperclass` 추상화(현재 반복 수준에서 시기상조).
- 결과: `./gradlew build` 재실행 전체 통과(신규 회귀 테스트 1개 포함, 총 6개). 자체 리뷰 1라운드로 마무리.
- 검증 레벨: **Level 1 PASS** · **Level 3 PASS**.

## Attempt 3 — 2026-07-15  ✅ PASS (패키지 구조 재구성)

- 시도: 사용자 피드백 반영 — 계층 우선(`com.coffeeorder.entity`/`repository`에 5개 도메인이 섞이는 구조)에서 **도메인 우선**(`com.coffeeorder.domain.{member,point,menu,order}.{entity,repository}`)으로 패키지 재구성. `point`/`PointHistory`는 한 도메인(`point`)으로 묶음(잔액+감사이력, ADR-004와 일관). `DataSeeder`는 cross-domain이라 `com.coffeeorder.config`에 그대로 둠. `docs/code-convention.md`에 이 규칙을 명문화(향후 controller/service/dto도 같은 도메인 패키지 하위에 위치).
- 결과: `./gradlew build` 전체 통과(패키지만 이동, 로직 변경 없음). `EntityMappingTest`를 `com.coffeeorder.domain`(cross-domain 통합 테스트)으로 이동.
- 검증 레벨: **Level 1 PASS** · **Level 3 PASS**.

## Attempt 4 — 2026-07-15  ✅ PASS (엔티티명·테이블명·생성자 컨벤션 변경)

- 시도: 사용자 피드백 반영 — ① `Orders` 엔티티/리포지토리를 `Order`/`OrderRepository`로 리네이밍(엔티티명은 단수로 통일). ② 5개 엔티티 전부 `@Table(name=...)`을 복수형으로 변경(`member`→`members`, `point`→`points`, `point_history`→`point_histories`, `menu`→`menus`, `orders`는 이미 복수형 유지). ③ 각 엔티티 no-arg 생성자를 손으로 쓴 `protected Entity() {}` 대신 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`(Lombok)로 교체, 필드(비즈니스) 생성자는 그대로 직접 작성 유지.
- 반영 문서: `docs/db/erd.md`(ERD 표기 ORDERS→ORDER, "공통 규칙"에 엔티티=단수/테이블=복수 명시) + 5개 테이블 md(헤더에 엔티티명 병기, FK 참조를 복수형 테이블명으로 수정) + `docs/code-convention.md`(엔티티 생성자·`@Table` 네이밍 규칙 갱신, 기존 "Lombok 생성자 애노테이션 금지" 규칙을 no-arg 한정 허용으로 수정) + `docs/dev/issue-roadmap.md`(#2 목표 엔티티명 갱신). `docs/adr/ADR-004`는 결정 당시 기록이라 유지(그대로 두면 `ORDER 헤더`라는 기각안 표기와 혼동될 위험도 있어 의도적으로 손대지 않음).
- 결과: 로컬 DB를 초기화(`DROP DATABASE` 후 재생성)하고 `docker compose up -d mysql` 대상으로 `./gradlew build` 재확인 — 6개 테스트 전부 PASS, `SHOW TABLES`로 `members`/`points`/`point_histories`/`menus`/`orders` 5개 테이블 생성 확인.
- 검증 레벨: **Level 1 PASS** · **Level 3 PASS** · **Level 5 PASS**.

## Attempt 5 — 2026-07-15  ✅ PASS (연관관계 객체 → FK id 전환)

- 시도: 사용자 질문("연관관계를 매핑하는데 객체 자체를 가지고 있을 필요가 있는가?") 검토 후 결정. `docs/api/order.md`·`point.md`·`ranking.md` 응답 스펙을 확인해 연관 엔티티 필드를 직접 노출하는 응답이 하나도 없음을 확인하고, `Point.member`(`@OneToOne`)·`PointHistory.member`(`@ManyToOne`)·`Order.member`/`Order.menu`(`@ManyToOne`)를 전부 제거하고 `Long memberId`/`Long menuId` 컬럼으로 교체. 근거를 [ADR-005](../../adr/ADR-005-엔티티간-FK-ID-참조.md)로 기록.
- 반영 문서: `docs/code-convention.md`(도메인 간 참조는 FK id만 보유 규칙 추가), `docs/db/point.md`·`point-history.md`·`orders.md`(FK 컬럼에 "DB 제약 아님, ADR-005 참고" 각주 추가), `docs/dev/ongoing/issue-2-domain-entity.md`.
- 결과: 로컬 DB 재생성 후 `./gradlew build` 재확인 — 6개 테스트 전부 PASS(1:1 unique·order_group_id unique 테스트는 `@JoinColumn` 없이도 `@Table(uniqueConstraints=...)`만으로 그대로 동작). `SHOW CREATE TABLE`로 FK 제약(`CONSTRAINT ... FOREIGN KEY`)이 더는 생성되지 않고, `uk_member_id`/`uk_order_group_id`/`idx_created_menu`는 그대로 유지됨을 확인.
- 검증 레벨: **Level 1 PASS** · **Level 3 PASS**.
