# 데이터 수집 플랫폼 전송 컨슈머

대상: collector/consume
이슈: [#6](https://github.com/yxejxnn/coffee-order-system/issues/6)

## 배경 / 요구
발제 3번 — 주문내역을 데이터 수집 플랫폼으로 실시간 전송(Mock).

## 설계 (HOW)
- 신규 패키지 `com.coffeeorder.domain.collector`: `consumer.CollectorEventConsumer`(`@KafkaListener`, groupId=`collector-group`) → `client.CollectorClient`(`RestClient`로 `POST /mock/collector/orders`, 최대 3회 재시도 후 로그, 예외 미전파) → `dto.CollectorTransmitRequest`(memberId, menuId, amount).
- 실제 외부 플랫폼이 없어 `mock.MockCollectorController`를 같은 앱에 두어 로컬 데모에서 실제 HTTP 호출을 관찰할 수 있게 함.
- 전송 실패 정책(사용자 확정): 3회 재시도 후 ERROR 로그만 남기고 종료, DLT·Outbox 없음(ADR-002와 동일 톤).
- 테스트는 WireMock 대신 `spring-boot-starter-test`에 이미 포함된 `MockRestServiceServer`(Spring 6.1+가 `RestClient` 지원) 재사용 — 신규 테스트 의존성 없음.
- 상세 설계는 [`design.md`](../collector/consume/design.md) 참고(SSOT).

## 관련 결정·질문
- [`docs/api/order.md`](../../api/order.md) · [`ADR-002`](../../adr/ADR-002-주문이벤트-비동기전달.md)
- ~~Open: 전송 실패 시 재시도/보관 정책~~ → Resolved(3회 재시도 후 로그), `docs/open-questions.md` 갱신.

## 태스크
- [x] `com.coffeeorder.domain.collector` 패키지(consumer/client/dto/mock) 구현
- [x] `application.yml`에 `spring.kafka.consumer.*`(Jackson3 네이티브 직렬화) · `collector.api.base-url` 추가
- [x] `CollectorClientTest`(성공/재시도소진) · `CollectorEventConsumerTest`(컨슈머 위임) 작성
- [x] `./gradlew test` 전체 통과 확인 + 로컬 `bootRun` 데모로 Mock 수신 확인
- [x] `design.md` 작성, `open-questions.md` Resolved 반영

## 평가(통과) 기준
- 이벤트 → Mock 전송 호출 확인(Mock/WireMock), 실패 처리 검증 — **Level 4~5**. (`MockRestServiceServer` 단위 테스트 + 로컬 `bootRun` 데모로 충족)
