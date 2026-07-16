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

## Attempt 2 — 2026-07-16  ✅ PASS
- 시도: PR #27 자체 리뷰(`/code-review --comment`, 8개 앵글 서브에이전트 → 후보 다수 중 중복 제거 후 7건을 PR 인라인 코멘트로 게시). 발견 순위:
  1. **적용** — `CollectorClient`가 빌드하는 `RestClient`에 타임아웃이 없어, 수집 플랫폼이 응답 없이 멈추면 유일한 `collector-group` 컨슈머 스레드가 무한정 블로킹될 수 있음. **수정**: HTTP 클라이언트 구성을 신규 `CollectorRestClientConfig`(`@Configuration`)로 분리하고 connect 2초/read 3초 타임아웃 추가. 부수 효과로 `CollectorClient`는 완성된 `RestClient`만 주입받게 돼 `@Value` 때문에 직접 쓰던 생성자를 다시 `@RequiredArgsConstructor`로 되돌림.
  2. **적용** — `collector.api.base-url` 기본값이 `server.port`와 무관하게 `8080`으로 고정돼, 데모 중 `SERVER_PORT`만 바꿨다가 실제로 겪었던 문제(001-consume.md 위 Attempt 1의 8082/8083 데모 참고). **수정**: `http://localhost:${server.port}`로 변경.
  3. **적용** — `CollectorTransmitRequest.from(event)` 호출이 재시도 try/catch 바깥에 있어, event가 null인 극단 케이스에서 NPE가 재시도 정책을 벗어나 전파될 수 있음. **수정**: `send()` 진입 시 null 가드 추가.
  4. **적용**(정리) — `groupId = "collector-group"`이 매직 스트링, `"/mock/collector/orders"`가 두 파일에 리터럴로 중복. **수정**: 각각 `CollectorEventConsumer.GROUP_ID` 상수·`MockCollectorController.ORDERS_PATH` 공개 상수로 추출.
  5. **적용**(문서) — `docs/dev/order/create/design.md`가 여전히 `JsonSerializer`를 언급(#5 이후 stale). **수정**: `JacksonJsonSerializer`로 정정하고 #6에서 발견했음을 명시.
  6. **적용**(문서) — `auto-offset-reset: earliest`가 신규 컨슈머 그룹 첫 기동 시 유실은 막지만 반대로 기존 토픽 이력을 한 번에 재처리하는 트레이드오프가 있음(이 프로젝트 환경에선 무해). **수정**: `docs/dev/collector/consume/design.md`에 트레이드오프로 명시(코드는 유지).
  7. **반영 안 함**(사용자의 기존 결정과 상충 — 되돌리지 않고 PR 코멘트로 사유만 남김) — `MockCollectorController`의 `ResponseEntity<Void>`가 프로젝트 컨벤션(`ResponseEntity<ApiResponse<T>>`)을 문자 그대로 벗어남. Plan 단계에서 승인된 설계(외부 플랫폼 흉내라 우리 응답 봉투를 강제할 이유 없음)라 코드는 유지.
  - 수정 과정에서 **테스트 버그 1건 추가 발견**: `MockRestServiceServer.bindTo(builder)`로 목을 심은 뒤 `CollectorClient` 생성자가 같은 builder에 `.requestFactory(...)`를 다시 호출해 목 팩토리를 실제 팩토리로 덮어써, 테스트가 실제 네트워크(`mock-collector` 호스트 DNS 실패)로 나가며 깨짐. HTTP 클라이언트 구성을 `CollectorRestClientConfig`로 옮기면서 `CollectorClientTest`도 "builder 생성 → mock 바인딩 → build() → 완성된 RestClient를 CollectorClient에 주입"(순서 고정) 형태로 `@BeforeEach`에 정리해 함께 해결.
- 결과: `./gradlew test`(전체) 42건 전부 PASS(회귀 없음). 로컬 `bootRun`(포트 8083, `COLLECTOR_API_BASE_URL` 미지정)으로 `collector.api.base-url` 자동 유도 재확인 — Mock 수신 로그 정상 출력.
- 검증 레벨: Level 1(단위, 회귀 포함) PASS · Level 5(로컬 `bootRun`, 실제 HTTP, base-url 자동 유도 확인) PASS.
