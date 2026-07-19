# 001-hardening — CollectorClient backoff + @Qualifier 하드닝 (로그)

## Attempt 1 — 2026-07-19  ✅ PASS
- 시도: `CollectorClient.restClient` 필드에 `@Qualifier("collectorRestClient")` 추가(#46). `send()`에 200ms 고정 backoff(`RETRY_BACKOFF_MS`, `sleepBackoff()`) 추가(#44) — 마지막 시도 실패 시엔 대기하지 않고 즉시 종료, 대기 중 인터럽트되면 인터럽트 상태 복원 후 재시도 포기.
- 결과: `./gradlew test --tests "*CollectorClientTest*"` 2건 전부 PASS. `send_doesNotThrow_afterExhaustingRetriesOnRepeatedFailure`는 backoff 2회(400ms)分 느려져 0.607s로 증가했으나 실패 없음. `./gradlew compileJava` PASS.
- 검증 레벨: Level 1(단위, 기존 `CollectorClientTest` 재사용) PASS. HTTP 왕복(Level 6)은 이 클라이언트가 이미 `MockRestServiceServer`로 왕복을 재현하는 단위 테스트를 갖고 있어 별도 실행 불필요로 판단.
