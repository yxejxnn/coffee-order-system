# order/create — Design

## 개요
회원이 메뉴를 주문·결제한다. 회원·메뉴 검증 후 `POINT`를 비관적 락으로 차감하고 `ORDERS`를 저장하는 하나의 트랜잭션으로 처리하며, 커밋 후 `OrderCompletedEvent`를 Kafka로 발행한다. 이 이슈에서 **Kafka producer 공통 설정을 확정**해 이후 컨슈머 이슈(#6·#7)가 재사용한다.

## API / 인터페이스
- `POST /api/orders` — 상세 계약(요청/응답/에러 코드)은 `docs/api/order.md` 참조.
- 구현: `domain/order/controller/OrderController` → `domain/order/service/OrderService#create` → (포인트 차감은 `domain/point/service/PointService#use` 재사용, #4의 락 폴백 그대로 사용) → `domain/order/repository/OrderRepository` → `domain/order/event/OrderCompletedEvent` 발행 → `domain/order/event/OrderCompletedEventListener`(`@TransactionalEventListener(AFTER_COMMIT)`)가 Kafka로 전송.
- 검증 순서(#42에서 재정리, #37에서 멱등 조회 추가): quantity(null이면 1, `<=0`이면 `INVALID_QUANTITY`, DB 호출 없이 가장 먼저) → **`Idempotency-Key` 헤더가 있으면 `OrderRepository#findByIdempotencyKey`로 먼저 조회, 있으면 여기서 즉시 그 주문 + 현재 잔액으로 반환(결제·나머지 검증 전부 스킵)** → 회원 존재(`MemberRepository#existsById`, 없으면 `MEMBER_NOT_FOUND`) → 메뉴 조회(`MenuRepository#findById`, 없으면 `MENU_NOT_FOUND`) → `PointService#use`(락 획득 → 잔액부족이면 `INSUFFICIENT_POINT`, 차감 없음) → `Order` 저장(`idempotencyKey` 포함) → 이벤트 발행. quantity를 맨 앞으로 옮긴 이유: 원래는 메뉴 조회 뒤에 검증해서, menuId·quantity가 둘 다 무효면 `MENU_NOT_FOUND`만 보고 quantity 문제를 못 보는 문제가 있었음(#35).

## 데이터 모델
- `orders`에 주문 1건(단가 스냅샷 `unit_price`, `total_price = unit_price * quantity`, `order_group_id` UUID)을 저장하고, 같은 트랜잭션에서 `point_histories`에 `USE` 이력을 남긴다(`order_group_id` 포함). 상세 스펙: `docs/db/orders.md`.
- 포인트 차감은 `PointService#use(memberId, amount, orderGroupId)`로 위임한다 — `PointService#charge`가 이미 가진 "`findByMemberIdForUpdate` 락 → 없으면 회원존재확인 후 생성" 폴백을 그대로 재사용하기 위함(중복 구현·락 순서 불일치 방지). `Point#use(amount)`는 `charge`와 대칭으로 단순 차감만 하고, 잔액 검증(`balance < amount` → `INSUFFICIENT_POINT`)은 서비스에서 락 획득 직후 수행한다.

## 규칙 / 검증
- **주문 요청 멱등성(#37)**: `orderGroupId`는 서버가 매 호출마다 새로 생성하므로, 클라이언트가 타임아웃 후 같은 논리적 요청을 재시도하면 원래는 새 주문 + 포인트 이중 차감이 났다. `Idempotency-Key` 헤더(선택, 클라이언트 발급)를 받아 `orders.idempotency_key`(UNIQUE, nullable)로 막는다 — 헤더가 있으면 결제 전에 먼저 조회해서, 이미 처리된 키면 결제·검증을 건너뛰고 그 주문 결과를 그대로 반환한다. 응답용 잔액은 `PointService#getBalance`(비잠금 단순 조회, 신설 — `charge`/`use`의 비관적 락과는 별개)로 조회한다.
  - 최소 스코프라 안 한 것: 같은 키인데 요청 body가 다른 경우 검증 안 함(그대로 첫 결과 반환). 동시에 같은 키로 2건이 동시에 "처리 안 됨"을 관찰하는 좁은 경합은 안 막음 — `orders` 저장에서 하나만 유니크 제약을 통과하고 나머지는 트랜잭션 전체 롤백(이중 차감은 없음)되지만 그 요청 자체는 실패한다. 상세: `docs/api/order.md` 규칙 절.
- **`memberRepository.existsById` 중복 체크(#42, 의도적으로 유지)**: `OrderService.create()`와 `PointService`(내부 `ensurePointCreated`, Point 없는 회원 부트스트랩 경로) 양쪽에 있다. `PointService` 쪽은 `PointController#charge`(사전 회원 검증이 없는 직접 API 진입점)의 정확성에 필수라 없앨 수 없고, `OrderService` 쪽을 없애면 회원 미존재 요청에서도 `menuRepository.findById`가 먼저 실행되는 불필요한 조회 + 에러 우선순위 역전(`MEMBER_NOT_FOUND` → `MENU_NOT_FOUND`)이라는 회귀가 생겨 그대로 둔다.
- 동시성 제어는 #4와 동일하게 `POINT` 행 비관적 락으로 한다. 근거: `docs/policy/point.md`, [ADR-001](../../../adr/ADR-001-포인트-동시성제어.md).
- quantity 유효성(`<=0` → `INVALID_QUANTITY`)은 Bean Validation이 아니라 서비스에서 직접 검사한다 — `PointChargeRequest.amount`와 동일 패턴. `GlobalExceptionHandler`는 Bean Validation 실패를 전부 `COMMON_001`로 매핑하는데, API 계약은 `ORDER_001`을 요구하기 때문.
- **Kafka producer 공통 설정**: `application.yml`의 `spring.kafka.producer`(`key-serializer`=StringSerializer, `value-serializer`=JacksonJsonSerializer, `retries: 3`). ADR-002의 "발행 실패 시 N회 재시도"는 커스텀 재시도 루프 대신 Kafka producer 자체 `retries` 설정으로 충족한다(더 단순·표준적). 토픽명은 `com.coffeeorder.config.KafkaTopics.ORDER_COMPLETED`(cross-domain 상수라 `config` 패키지, #6·#7이 그대로 참조).
  - **직렬화 클래스 정정(#6에서 발견)**: 최초 도입 시 `JsonSerializer`(Jackson 2 API 의존)를 썼으나, Boot 4.1 기본 Jackson이 3.x라 `bootRun` 시 `NoClassDefFoundError`로 실패함을 #6에서 발견 — `JacksonJsonSerializer`(Jackson 3 네이티브)로 교체했다. 상세는 `docs/dev/collector/consume/design.md`·`docs/logs/collector/consume/001-consume.md` 참고.
  - **의존성 주의(비직관적)**: `build.gradle`에 `org.springframework.kafka:spring-kafka`만 추가하면 Spring Boot 4.1의 `KafkaTemplate` 자동설정 빈이 생성되지 않는다 — Boot 4는 Kafka 자동설정을 별도 모듈(`org.springframework.boot:spring-boot-kafka`)로 분리했고, 이는 `org.springframework.boot:spring-boot-starter-kafka`를 통해서만 전이 의존으로 들어온다(`spring-boot-data-jpa`/`spring-boot-data-redis`가 각각의 `starter-data-jpa`/`starter-data-redis`로 들어오는 것과 같은 패턴). 그래서 `build.gradle`은 `spring-kafka` 대신 `spring-boot-starter-kafka`를 쓴다. 향후 #6·#7이 `@KafkaListener`/`ConsumerFactory`를 쓸 때도 이 의존성이 이미 있으므로 추가 조치 불필요.
- `OrderCompletedEventListener`가 커밋 후에만 발행되는지, 잔액 부족으로 롤백되면 발행되지 않는지는 `@EmbeddedKafka` 통합 테스트(`OrderCompletedEventListenerTest`)로 검증한다.
- 검증 레벨: Level 1(단위)·Level 2(컨트롤러 계약)·**Level 4(락·동시성 통합, `OrderServiceConcurrencyTest`)**·**Level 5(커밋 후 발행, `OrderCompletedEventListenerTest`)**·Level 6(실제 HTTP) PASS. 상세 근거는 `docs/logs/order/create/001-create.md`, `docs/logs/order/create/003-idempotency.md`.

## 관련 문서
- `docs/api/order.md` · `docs/db/orders.md` · `docs/policy/point.md` · [ADR-001](../../../adr/ADR-001-포인트-동시성제어.md) · [ADR-002](../../../adr/ADR-002-주문이벤트-비동기전달.md) · [ADR-004](../../../adr/ADR-004-데이터모델-포인트분리.md)
- `docs/dev/point/charge/design.md` (재사용한 락 패턴의 원본, `getBalance` 추가 반영)
