# 001-consume — 데이터 수집 플랫폼 전송 컨슈머 (로그)

## Attempt 1 — 2026-07-16  ✅ PASS
- 시도: `com.coffeeorder.domain.collector` 패키지(`consumer.CollectorEventConsumer`/`client.CollectorClient`/`dto.CollectorTransmitRequest`/`mock.MockCollectorController`) 신규 구현. 사용자가 확정한 전송 실패 정책(최대 3회 재시도 후 ERROR 로그, DLT·Outbox 없음)을 `CollectorClient.send()`에 반영. 실제 외부 플랫폼이 없어 `MockCollectorController`를 같은 앱에 둬 로컬 데모에서 실제 HTTP 호출을 확인할 수 있게 함. `application.yml`에 `spring.kafka.consumer.*`(이 이슈에서 최초 도입)와 `collector.api.base-url` 추가.
- 중간 이슈 1 — **`RestClient.Builder` 빈 부재**: `CollectorClient` 생성 시 전체 스위트 중 `OrderServiceConcurrencyTest`·`PointServiceConcurrencyTest`·`OrderCompletedEventListenerTest` 3건이 `NoSuchBeanDefinitionException(RestClient.Builder)`로 컨텍스트 로딩 실패. 원인: Spring Boot 4.1이 `RestClient` 자동설정을 `spring-boot-starter-web`이 아닌 별도 `spring-boot-restclient` 모듈로 분리함(#5의 `spring-boot-starter-kafka` 분리와 같은 패턴). **해결**: `build.gradle`에 `org.springframework.boot:spring-boot-restclient` 추가.
- 중간 이슈 2 — **컨슈머 테스트에서 "zero interactions"**: `CollectorEventConsumerTest`가 메시지를 보냈는데도 `CollectorClient.send()`가 전혀 호출되지 않음. 원인: 컨슈머 그룹(`collector-group`)이 첫 기동이라 커밋된 오프셋이 없는 상태에서 기본 `auto.offset.reset=latest`를 타 컨슈머 그룹 조인 완료 시점(리밸런스에 약 0.5초 소요)보다 프로듀서 전송이 먼저 끝나 메시지를 건너뜀(레이스). **해결**: `spring.kafka.consumer.auto-offset-reset: earliest`로 설정 — 신규 컨슈머 그룹의 첫 기동 시 토픽 처음부터 읽도록 해 테스트 레이스뿐 아니라 실배포 시 첫 기동 유실도 방지.
- 중간 이슈 3 — **`bootRun` 로컬 데모 중 컨슈머 기동 실패(`NoClassDefFoundError: com.fasterxml.jackson.databind.JavaType`)**: 테스트는 전부 통과했는데 실제 `bootRun`에서만 실패. 원인: Boot 4.1 기본 Jackson은 3.x(`tools.jackson.*`)인데 `spring-kafka`의 구 `JsonSerializer`/`JsonDeserializer`(#5에서 프로듀서에 도입, 이번에 컨슈머에도 그대로 씀)는 Jackson 2(`com.fasterxml.jackson.databind`) API에 의존 — 프로덕션 런타임 클래스패스엔 Jackson 2 databind가 없고(테스트 클래스패스에만 `spring-boot-starter-test`의 전이 의존으로 존재) 테스트가 이 차이를 가려 놓쳤다. **해결**: 프로듀서·컨슈머 모두 `spring-kafka`가 제공하는 Jackson 3 네이티브 `JacksonJsonSerializer`/`JacksonJsonDeserializer`로 교체(설정 프로퍼티 키는 `JsonSerializer`/`JsonDeserializer`와 동일해 마이그레이션 비용 없음). #5에서 도입한 프로듀서 설정도 함께 고쳤음을 명시.
- 결과: `./gradlew test`(전체, 실 MySQL·Redis·Kafka 대상) 42건 전부 PASS(신규: `CollectorClientTest` 2·`CollectorEventConsumerTest` 1).
- 검증 레벨:
  - Level 1(단위: `CollectorClientTest` — `MockRestServiceServer`로 성공 1회 호출·실패 3회 재시도 후 무전파 모두 검증) PASS.
  - Level 4~5(컨슈머 위임 + 실제 Kafka, `CollectorEventConsumerTest` + `@EmbeddedKafka`) PASS — 토픽에 실제 이벤트를 발행하면 `CollectorEventConsumer`가 이를 수신해 `CollectorClient.send()`를 정확한 페이로드로 호출함을 확인.
  - Level 5(로컬 `bootRun` + `docker compose` 인프라, 실제 HTTP) PASS — 아래 증거 참고.
- 증거(API 샘플, `bootRun` 포트 8082로 데모 — 8080은 IDE에서 띄운 별도 인스턴스와 충돌 방지):
  ```
  POST /api/orders {"memberId":7,"menuId":3,"quantity":1}
  → 201 {"code":"SUCCESS","data":{"orderGroupId":"0925aed5-833d-45c3-8751-d4364d34b288","memberId":7,"menuId":3,"quantity":1,"totalPrice":4500,"balance":12500}}
  ```
  앱 로그: `[Mock 데이터 수집 플랫폼] 수신: memberId=7, menuId=3, amount=4500` — 주문 → Kafka(`order-completed`) → `collector-group` 컨슈머 → Mock API까지 실제 HTTP로 확인.
