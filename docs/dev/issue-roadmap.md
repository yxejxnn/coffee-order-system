# 이슈 로드맵 (Plan 산출물)

발제를 **기능 단위로 쪼갠 이슈 백로그**(10개)다. 게이트 A(로드맵 승인) 대상이며, GitHub Issue와 1:1 대응한다.

> 원칙(`docs/workflow/plan-guide.md`): 이슈 하나 = 한 가지 일. **WHAT(목표·완료조건·영향범위)은 미리 상세히**, **HOW는 집을 때** `docs/dev/ongoing/`에 채운다. 공유 구현(엔티티·공통 서비스·락 패턴·Kafka 설정)은 미리 박지 않고 **선행 이슈에서 확정**한다.

## 의존 순서

```
1 foundation ─ 2 entity ─┬─ 3 menu/list
                         ├─ 4 point/charge ─┐
                         └─ 5 order/create ◄─┘ (4의 락 패턴 재사용, 커밋 후 이벤트 발행 포함)
                                  │
                                  ├─ 6 collector/consume
                                  └─ 7 ranking/consume ─ 8 ranking/popular
9 verify/multi-instance  (3~8 통합)
10 docs/readme           (발제 0번, 맨 마지막)
```

- **선행 확정**: 비관적 락 리포지토리 패턴은 **#4에서 도입**하고 #5가 재사용한다. Kafka producer/공통 설정은 **#5에서 확정**하고 #6·#7이 재사용한다.

---

## 이슈 목록

### #1 setup/foundation: 프로젝트 뼈대 + 인프라(Docker Compose)
- **목표**: 빌드·기동되는 최소 프로젝트 + 로컬 인프라. `build.gradle`(SB 4.1.0, Java 17 소스 / JDK 21 toolchain), 패키지 계층(`com.coffeeorder`의 controller/service/repository/entity/dto), 공통 응답 래퍼 `ApiResponse<T>`, `@RestControllerAdvice` 전역 예외 + 에러코드 enum 골격, `docker-compose.yml`(MySQL·Redis·Kafka 단일 브로커 KRaft), `application.yml` 프로파일(크리덴셜 fail-fast) + `.env`.
- **완료조건**: `./gradlew build` 성공, `docker compose up` 후 앱이 3인프라에 연결 기동(Level 5). ApiResponse/예외 핸들러 단위 테스트.
- **영향범위**: config, common, compose, application.yml.
- **관련 문서**: `docs/code-convention.md`.
- **선행**: 없음.

### #2 domain/entity: 도메인 엔티티 + 스키마 + 시드
- **목표**: `Member`·`Point`·`PointHistory`·`Menu`·`Order` JPA 엔티티 + 회원·메뉴 시드 데이터. (생성자·Lombok 정책은 `docs/code-convention.md` 참고 — Generate 중 구체화됨)
- **완료조건**: 엔티티 ↔ `docs/db/` 명세 일치, 스키마 생성/기동(Level 5), 1:1(Point)·FK 매핑 검증 테스트.
- **영향범위**: entity, repository(기본), 초기 데이터.
- **관련 문서**: `docs/db/erd.md` + 각 테이블 md, `docs/adr/ADR-004`.
- **선행**: #1.

### #3 menu/list: 커피 메뉴 목록 조회 API
- **목표**: `GET /api/menus` — 메뉴 id·name·price 목록.
- **완료조건**: 200 + 목록/빈 목록 `[]`. 컨트롤러·서비스 테스트(Level 3~4), 실제 HTTP(Level 6).
- **영향범위**: menu controller/service/repository/dto.
- **관련 문서**: `docs/api/menu.md`.
- **선행**: #2.

### #4 point/charge: 포인트 충전 API (+동시성 락)
- **목표**: `POST /api/points/charge` — 양수 검증, `POINT` 행 **비관적 락**으로 잔액 증가 + `POINT_HISTORY(CHARGE)`(동일 트랜잭션). **락 리포지토리 패턴을 이 이슈에서 확정**한다.
- **완료조건**: 정상 충전 + 잔액 반환, `INVALID_AMOUNT`/`MEMBER_NOT_FOUND`. **동시 충전 N스레드 테스트로 lost update 0**(Level 4).
- **영향범위**: point controller/service/repository/dto.
- **관련 문서**: `docs/api/point.md`, `docs/policy/point.md`, `docs/adr/ADR-001`.
- **선행**: #2.

### #5 order/create: 커피 주문/결제 API (+락·트랜잭션 +이벤트 발행)
- **목표**: `POST /api/orders` — 회원·메뉴 검증, `POINT` 비관적 락(#4 재사용) → 잔액 확인·차감 → `ORDERS` 저장(단가 스냅샷, `order_group_id` 발급) → `POINT_HISTORY(USE)`, 하나의 트랜잭션. **커밋 후**(`@TransactionalEventListener(AFTER_COMMIT)`) `OrderCompletedEvent`를 Kafka로 발행. **Kafka producer/공통 설정을 이 이슈에서 확정**한다.
- **완료조건**: 201 + 주문/잔액, `INSUFFICIENT_POINT`(409, 차감 없음)·`MENU_NOT_FOUND`. **동시 주문 N스레드 테스트로 초과 차감 0·주문 수 정확**(Level 4). 커밋된 주문만 이벤트 발행(롤백 시 미발행)·토픽 적재 확인(Level 5), 실제 HTTP(Level 6).
- **영향범위**: order controller/service/repository/dto, point 차감 재사용, kafka config, event dto.
- **관련 문서**: `docs/api/order.md`, `docs/adr/ADR-001`, `ADR-002`, `ADR-004`.
- **선행**: #4.

### #6 collector/consume: 데이터 수집 플랫폼 전송 컨슈머
- **목표**: 이벤트 소비 그룹 (a) — Mock 데이터 수집 플랫폼 API로 `memberId, menuId, 결제금액` 전송(발제 3). 전송 실패 시 정책대로.
- **완료조건**: 이벤트 → Mock 전송 호출 확인(Mock/WireMock), 실패 처리 검증(Level 4~5).
- **영향범위**: consumer, 외부 전송 클라이언트(Mock).
- **관련 문서**: `docs/api/order.md`, `docs/adr/ADR-002`.
- **선행**: #5.

### #7 ranking/consume: 인기 메뉴 집계 컨슈머 (Redis ZSET + 멱등)
- **목표**: 이벤트 소비 그룹 (b) — `order_group_id` 멱등 체크(Redis SET) 후 일자 버킷 ZSET `menu:ranking:{yyyy-MM-dd}`에 `ZINCRBY`.
- **완료조건**: **같은 `order_group_id` 2회 전달 시 카운트 1만 증가**(멱등), 멀티 인스턴스 동시 증가 정확성(Level 4).
- **영향범위**: consumer, redis config/repository.
- **관련 문서**: `docs/adr/ADR-003`, `docs/policy/popular-menu.md`.
- **선행**: #5.

### #8 ranking/popular: 인기 메뉴 조회 API
- **목표**: `GET /api/menus/popular` — 최근 7일 일자 키 union 상위 3개(횟수 desc → menuId asc), 메뉴 이름/카운트 조립.
- **완료조건**: 7일 경계·union·정렬·3개 미만 케이스(Level 3~4), 실제 HTTP(Level 6).
- **영향범위**: ranking controller/service, redis 조회, menu 조인.
- **관련 문서**: `docs/api/ranking.md`, `docs/policy/popular-menu.md`, `docs/adr/ADR-003`.
- **선행**: #7.

### #9 verify/multi-instance: 다중 인스턴스 통합 검증
- **목표**: 앱을 **2개 인스턴스**로 띄우고(같은 MySQL/Redis/Kafka 공유) 동시성·일관성 e2e — 동시 주문/충전 시 잔액·주문수·랭킹 카운트 정확.
- **완료조건**: 2인스턴스 동시 요청 시나리오에서 초과 차감 0·카운트 정확(Level 5~6), 검증 로그 `docs/logs/`.
- **영향범위**: 통합 테스트/스크립트, compose(인스턴스 2개).
- **관련 문서**: ADR-001~003(도전 요구 종합).
- **선행**: #3~#8.

### #10 docs/readme: 문제 해결 전략 README (발제 0번, 필수)
- **목표**: 발제 필수 README — 설계 내용(ERD·API 명세 요약/링크), 설계 의도, 선택한 문제해결 전략·분석 근거, 기술 선택 이유, 실행법. `docs/db`·`docs/api`·`docs/adr` 종합.
- **완료조건**: 발제 0번 4개 항목 모두 충족.
- **영향범위**: README.md(제출용).
- **관련 문서**: 전체 `docs/`.
- **선행**: #1~#9.
