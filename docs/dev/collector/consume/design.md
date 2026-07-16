# collector/consume — Design

## 개요
주문 완료 후 발행되는 `OrderCompletedEvent`(`order-completed` 토픽)를 소비해, 주문내역(`memberId, menuId, 결제금액`)을 데이터 수집 플랫폼으로 실시간 전송한다(발제 3). 실제 외부 플랫폼이 없어 같은 애플리케이션 안에 Mock 수신 엔드포인트를 함께 둔다.

## 연동 인터페이스
- **입력**: Kafka `order-completed` 토픽, 컨슈머 그룹 `collector-group`. `OrderCompletedEvent`(`orderGroupId, memberId, menuId, totalPrice`)를 그대로 구독한다.
- **출력**: `POST {collector.api.base-url}/mock/collector/orders` — body `{memberId, menuId, amount}`. `collector.api.base-url` 기본값은 `http://localhost:8080`(자기 자신, 로컬 데모용).
- 받는 쪽 `MockCollectorController`는 페이로드를 로그로 남기고 200만 반환하는 순수 Mock이다.

## 실패 정책
최대 3회 시도(프로듀서 `retries: 3`과 동일 톤 — [ADR-002](../../../adr/ADR-002-주문이벤트-비동기전달.md)), 재시도 간 지연 없음. 3회 모두 실패하면 예외를 전파하지 않고 ERROR 로그만 남기며 종료한다 — Kafka 오프셋은 정상 커밋되어 재전달·별도 보관은 하지 않는다(부가 경로라 유실 허용). 랭킹 집계([#7](https://github.com/yxejxnn/coffee-order-system/issues/7))와 달리 멱등 처리는 하지 않는다 — 외부 전송은 중복 호출이 정확성에 영향을 주지 않기 때문이다.

## 관련 코드 위치
- `com.coffeeorder.domain.collector.consumer.CollectorEventConsumer` — `@KafkaListener`.
- `com.coffeeorder.domain.collector.client.CollectorClient` — `RestClient` 전송 + 재시도.
- `com.coffeeorder.domain.collector.dto.CollectorTransmitRequest` — 전송 페이로드.
- `com.coffeeorder.domain.collector.mock.MockCollectorController` — Mock 수신 엔드포인트.
- `application.yml`의 `spring.kafka.consumer.*`(이 이슈에서 최초 도입, [#7](https://github.com/yxejxnn/coffee-order-system/issues/7)이 재사용) · `collector.api.base-url`.

## 알려진 이슈 (이 작업에서 발견·수정)
Spring Boot 4.1 기본 Jackson은 3.x(`tools.jackson.*`)이지만 `spring-kafka`의 구 `JsonSerializer`/`JsonDeserializer`(#5에서 프로듀서에 도입)는 Jackson 2(`com.fasterxml.jackson.databind`) API에 의존한다. 프로덕션 런타임 클래스패스엔 Jackson 2 databind가 없어(테스트 클래스패스에만 전이 의존으로 존재) `bootRun` 시 `NoClassDefFoundError`로 컨슈머 기동이 실패했다 — 테스트는 이 차이 때문에 통과해 놓쳤던 문제다. `spring-kafka`가 제공하는 Jackson 3 네이티브 `JacksonJsonSerializer`/`JacksonJsonDeserializer`로 교체(프로듀서·컨슈머 모두)해 해결했다. `RestClient.Builder` 빈도 Boot 4에서 `spring-boot-starter-web`이 아닌 별도 `spring-boot-restclient` 모듈에서 제공돼 의존성을 추가했다.
