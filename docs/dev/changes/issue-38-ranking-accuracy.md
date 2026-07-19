# ranking 정확성 정리

대상: ranking/query, ranking/consume
이슈: [#38](https://github.com/yxejxnn/coffee-order-system/issues/38), [#41](https://github.com/yxejxnn/coffee-order-system/issues/41) (같은 PR로 묶어 처리)
담당: Claude

## 배경 / 요구
- #38: `RankingQueryService`의 클래스 레벨 `@Transactional(readOnly = true)`가 Redis I/O까지 감싸 DB 커넥션을 불필요하게 오래 붙잡음.
- #41: `RankingEventConsumer`에 DLT/재시도 정책이 없어 Redis 장애 시 랭킹 집계가 조용히 유실될 수 있음(정확성이 요구사항인데도).

## 설계
- #38: 클래스 레벨 `@Transactional` 제거(`menuRepository.findAllById`는 자체 트랜잭션으로 충분). 이 기능(`ranking/popular`, #8)에 `design.md`가 원래 없던 걸 발견해 이번에 새로 작성.
- #41: `KafkaTopics.ORDER_COMPLETED_DLT`(`order-completed-dlt`) 추가 + `KafkaErrorHandlingConfig`(`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`, `FixedBackOff(500L, 2)`)를 전역 `CommonErrorHandler`로 등록. collector-group은 예외를 던지지 않아 영향 없음. 구현 중 DLT 토픽명에 `.`(점)을 쓰면 macOS(APFS, 대소문자 미구분)에서 임베디드 브로커가 죽는 걸 재현해 `-dlt`(하이픈)로 정정(상세: `docs/logs/ranking/consume/002-dlt.md`).

## 관련 결정·질문
- `docs/policy/popular-menu.md`에 실패 정책 명시 완료

## 태스크
- [x] `RankingQueryService`에서 `@Transactional` 제거 + `docs/dev/ranking/popular/design.md` 신규 작성
- [x] `KafkaTopics.ORDER_COMPLETED_DLT` 추가
- [x] `KafkaErrorHandlingConfig` 신설
- [x] DLT 도착 검증 테스트 추가(`RankingEventConsumerTest`)
- [x] design.md·policy 문서 갱신

## 평가(통과) 기준
- 기존 ranking 테스트 회귀 없음 — 확인 완료
- 반복 실패 이벤트가 재시도 소진 후 DLT 토픽에 도착함을 실제 EmbeddedKafka로 검증 — 확인 완료
- 검증 레벨: Level 1(단위+회귀), Level 4(Kafka 통합) PASS
