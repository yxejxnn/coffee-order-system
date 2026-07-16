# 커피 주문/결제 API (+락·트랜잭션 +이벤트 발행)

대상: order/create
이슈: [#5](https://github.com/yxejxnn/coffee-order-system/issues/5)

## 배경 / 요구
발제 3번 — 주문/결제 + 커밋 후 이벤트 발행. **Kafka producer/공통 설정을 이 이슈에서 확정**(#6·#7이 재사용).

## 설계 (HOW)
- **포인트 차감은 `PointService`에 위임.** `PointService.charge()`가 이미 가진 "`findByMemberIdForUpdate` 락 → 없으면 회원존재확인 후 생성" 폴백을 order에서 재구현하지 않도록, 대칭 메서드 `PointService.use(memberId, amount, orderGroupId)`를 추가해 `OrderService`가 이 메서드 하나만 호출한다(같은 트랜잭션에 join, propagation REQUIRED 기본값).
- `PointService.use()`: 락 획득(폴백 재사용) → `balance < amount`면 `INSUFFICIENT_POINT` → `Point.use(amount)`(대칭 차감 메서드, 엔티티는 검증 없이 단순 차감) → `PointHistory(USE, orderGroupId)` 저장.
- `OrderService.create(memberId, menuId, quantity)`: 회원 존재(`existsById`) → 메뉴 조회(`MENU_NOT_FOUND`) → quantity null→1·`<=0`이면 `INVALID_QUANTITY`(**Bean Validation이 아니라 서비스 수동 검사** — `PointChargeRequest.amount`와 동일 패턴. `GlobalExceptionHandler`가 Bean Validation 실패를 전부 `COMMON_001`로 묶어서, `ORDER_001` 계약을 지키려면 수동 검사가 필요) → `totalPrice = unitPrice * quantity` → `orderGroupId` UUID 발급 → `pointService.use(...)` → `Order` 저장(단가 스냅샷) → `eventPublisher.publishEvent(OrderCompletedEvent)`.
- Kafka 공통 설정(#6·#7 재사용): `application.yml`의 `spring.kafka.producer`(String/Json 직렬화, `retries: 3`)와 `com.coffeeorder.config.KafkaTopics.ORDER_COMPLETED` 상수. "N회 재시도"는 커스텀 루프 대신 producer 자체 retry로 충족.
- `OrderCompletedEventListener`(`@TransactionalEventListener(AFTER_COMMIT)`)가 `KafkaTemplate<String, OrderCompletedEvent>`로 발행, 실패 시 로그만.

## 관련 결정·질문
- [`docs/api/order.md`](../../api/order.md)
- [`ADR-001`](../../adr/ADR-001-포인트-동시성제어.md) · [`ADR-002`](../../adr/ADR-002-주문이벤트-비동기전달.md) · [`ADR-004`](../../adr/ADR-004-데이터모델-포인트분리.md)

## 태스크
- [x] `Point.use()` + `PointService.use()` (락 재사용)
- [x] Kafka producer 공통 설정 + `KafkaTopics` 상수
- [x] `OrderCompletedEvent` + AFTER_COMMIT 리스너
- [x] Order DTO/Service/Controller
- [x] 단위·컨트롤러·동시성·Kafka 발행 테스트

## 평가(통과) 기준
- 201 + 주문/잔액, `INSUFFICIENT_POINT`(409, 차감 없음)·`MENU_NOT_FOUND`.
- **동시 주문 N스레드 테스트로 초과 차감 0·주문 수 정확** — **Level 4**.
- 커밋된 주문만 발행(롤백 시 미발행)·토픽 적재 — **Level 5**, 실제 HTTP **Level 6**.
