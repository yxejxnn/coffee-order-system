# 001-create — 커피 주문/결제 API (+락·트랜잭션 +이벤트 발행) (로그)

## Attempt 1 — 2026-07-16  ✅ PASS
- 시도: `docs/api/order.md`·ADR-001/002/004대로 `OrderController`/`OrderService`/`OrderRepository`/`OrderCreateRequest`·`OrderCreateResponse` 구현. 포인트 차감은 직접 `PointRepository`를 호출하지 않고 `PointService`에 대칭 메서드 `use(memberId, amount, orderGroupId)`를 추가해 #4의 락 폴백을 재사용(`Point`에도 `charge`와 대칭인 `use` 추가). quantity 검증은 `PointChargeRequest.amount`와 동일하게 서비스 수동 검사로 처리(`ORDER_001` 계약 때문). Kafka 공통 설정(`application.yml`의 `spring.kafka.producer` + `com.coffeeorder.config.KafkaTopics`)과 `OrderCompletedEvent`/`OrderCompletedEventListener`(`@TransactionalEventListener(AFTER_COMMIT)`) 신규 작성.
- 중간 이슈 1 — **`KafkaTemplate` 빈 부재**: `build.gradle`에 `org.springframework.kafka:spring-kafka`만 넣었더니 `OrderCompletedEventListener` 생성 시 `NoSuchBeanDefinitionException`(`KafkaTemplate<String, OrderCompletedEvent>` 빈 없음)으로 컨텍스트 로딩 실패. 원인: Spring Boot 4.1이 Kafka 자동설정을 `spring-boot-kafka` 모듈로 분리했고, 이는 `spring-boot-starter-kafka`를 통해서만 전이 의존으로 들어옴(`data-jpa`/`data-redis`와 같은 패턴). **해결**: `spring-kafka` → `org.springframework.boot:spring-boot-starter-kafka`로 교체. (자동설정 빈은 `KafkaTemplate<?, ?>`로 와일드카드 선언돼 있어, 이 의존성만 맞으면 구체 타입 주입 지점과도 정상 매칭된다.)
- 중간 이슈 2 — **DB 인증 실패로 오인**: 위 수정 후 `@SpringBootTest` 전체가 `HibernateException: Unable to determine Dialect`(내부적으로 `Access denied for user 'root'@'localhost'`)로 실패. MySQL 컨테이너에 `docker exec`로 직접 접속(동일 비밀번호)은 성공해 자격증명 자체는 문제가 아님을 확인. 원인은 **`DB_PORT` 환경변수 누락** — 로컬 호스트에 이미 떠 있는 네이티브 MySQL(3306, 무관한 별도 서비스)로 연결을 시도해 그 서버의 계정으로 인증이 거부된 것이었다(`DB_USERNAME`/`DB_PASSWORD`만 export하고 `DB_PORT=3307`을 빠뜨림). `DB_PORT=3307`을 함께 export하니 정상 연결.
- 결과: `./gradlew test`(전체, 실 MySQL·Redis·Kafka 대상) 39건 전부 PASS(신규: `OrderServiceTest` 6·`OrderControllerTest` 1·`OrderServiceConcurrencyTest` 1·`OrderCompletedEventListenerTest` 2).
- 검증 레벨:
  - Level 1(단위+회귀 전체) PASS.
  - Level 2(컨트롤러 계약: 정상/`INVALID_QUANTITY`/`MENU_NOT_FOUND`/`MEMBER_NOT_FOUND`/`INSUFFICIENT_POINT`) PASS.
  - **Level 4(락·동시성, `OrderServiceConcurrencyTest`)** PASS — 잔액을 정확히 5회분(4,500 × 5)만 충전한 회원에게 스레드풀(10)로 15개 동시 주문 요청 → 성공 정확히 5건·`INSUFFICIENT_POINT` 정확히 10건·최종 잔액 정확히 0(초과 차감 없음)·`orders` 행 정확히 5건.
  - **Level 5(커밋 후 발행, `OrderCompletedEventListenerTest` + `@EmbeddedKafka`)** PASS — 정상 주문은 커밋 후 토픽에 `orderGroupId`를 담은 메시지가 도착함을 확인, 잔액 부족으로 롤백되는 주문은 일정 시간 poll 후에도 메시지가 전혀 도착하지 않음을 확인(커밋되지 않은 주문은 발행되지 않음, ADR-002 요구사항 충족).
  - Level 6(실제 HTTP, `./gradlew bootRun` + `curl`) PASS.
- 증거(API 샘플):
  ```
  POST /api/orders {"memberId":9,"menuId":3}                    (잔액 10,000, 단가 4,500, 수량 기본1)
  → 201 {"code":"SUCCESS","data":{"orderGroupId":"5349e38c-...","memberId":9,"menuId":3,"quantity":1,"totalPrice":4500,"balance":5500}}
  POST /api/orders {"memberId":9,"menuId":3,"quantity":2}       (남은 잔액 5,500 < 필요 9,000)
  → 409 {"code":"POINT_002","message":"포인트 잔액이 부족합니다"}
  POST /api/orders {"memberId":9,"menuId":999999}
  → 404 {"code":"MENU_001","message":"존재하지 않는 메뉴입니다"}
  POST /api/orders {"memberId":999999,"menuId":3}
  → 404 {"code":"MEMBER_001","message":"존재하지 않는 회원입니다"}
  POST /api/orders {"memberId":9,"menuId":3,"quantity":0}
  → 400 {"code":"ORDER_001","message":"수량은 0보다 커야 합니다"}
  ```
  - 잔액 부족 주문(quantity:2) 시도 후 `POST /api/points/charge {"memberId":9,"amount":1}` → `{"balance":5501}`(5500+1) 확인 — 실패한 주문에서 차감이 전혀 일어나지 않았음을 재확인.

## Attempt 2 — 2026-07-16  ✅ PASS
- 시도: PR #25 자체 리뷰(`/code-review --comment`, 8개 앵글 서브에이전트 → 후보 다수 중 중복 제거 후 5건을 PR 인라인 코멘트로 게시). 발견 순위:
  1. **적용** — `PointService.use()`가 `charge()`와 달리 `amount` 유효성 검사가 없어 대칭이 깨짐(현재는 `OrderService`의 quantity 검증 덕에 도달 불가하지만 방어 목적). **수정**: `charge()`와 동일하게 `amount == null || amount <= 0` → `INVALID_AMOUNT` 가드 추가.
  2. **적용** — `Point.use()`가 `charge()`의 `Math.addExact`와 달리 무방비 `-=`. **수정**: `Math.subtractExact`로 교체.
  3. **적용** — `charge()`/`use()`가 락 획득+폴백 라인을 복붙. **수정**: `getLockedPoint(memberId)` private 헬퍼로 추출해 공유.
  4. **적용**(테스트 커버리지) — `PointService.use()`를 직접 겨냥한 단위 테스트 부재(간접 검증만 있었음). **수정**: `PointServiceTest`에 `use_*` 4건 추가(정상 차감/잔액부족/유효하지 않은 amount/Point 없음 폴백).
  5. **반영 안 함**(사용자의 기존 결정과 상충 — 되돌리지 않고 확인만 요청) — `use()`도 `charge()`와 같은 락 폴백을 타므로 Point 미생성 회원에 대한 동시 첫 접근 시 `uk_member_id` 충돌 이론적 가능성이 재사용 경로에도 남음. 이는 #4에서 이미 검토·수용된 트레이드오프(`docs/dev/point/charge/design.md`)라 이번 이슈에서 임의로 고치지 않고 PR 코멘트에 사유만 답글로 남김.
  - PR 인라인 코멘트 5건 중 4건 반영 후 push, 각 스레드 resolve. 1건(#5)은 답글만 남기고 미해결(unresolved) 상태로 게이트 B로 넘김.
- 결과: `./gradlew test`(전체, 실 MySQL·Redis·Kafka 대상) 43건 전부 PASS(신규 4건: `PointServiceTest.use_*`).
- 검증 레벨: Level 1(단위+회귀 전체) PASS.
