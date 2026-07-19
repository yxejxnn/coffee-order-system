# 001-hardening — CollectorClient backoff + @Qualifier 하드닝 (로그)

## Attempt 1 — 2026-07-19  ✅ PASS (자체 리뷰에서 #46 미해결 발견 → Attempt 2로 수정)
- 시도: `CollectorClient.restClient` 필드에 `@Qualifier("collectorRestClient")` 추가(#46). `send()`에 200ms 고정 backoff(`RETRY_BACKOFF_MS`, `sleepBackoff()`) 추가(#44) — 마지막 시도 실패 시엔 대기하지 않고 즉시 종료, 대기 중 인터럽트되면 인터럽트 상태 복원 후 재시도 포기.
- 결과: `./gradlew test --tests "*CollectorClientTest*"` 2건 전부 PASS. `send_doesNotThrow_afterExhaustingRetriesOnRepeatedFailure`는 backoff 2회(400ms)分 느려져 0.607s로 증가했으나 실패 없음. `./gradlew compileJava` PASS.
- 검증 레벨: Level 1(단위, 기존 `CollectorClientTest` 재사용) PASS. HTTP 왕복(Level 6)은 이 클라이언트가 이미 `MockRestServiceServer`로 왕복을 재현하는 단위 테스트를 갖고 있어 별도 실행 불필요로 판단.
- **자체 리뷰(`/code-review --comment`, high effort)에서 발견**: "Lombok이 `@Qualifier`를 필드에서 생성자 파라미터로 자동 복사한다"는 가정이 이 저장소엔 틀렸음(`lombok.config` 없음). `javap -v -p`로 컴파일된 생성자를 직접 까보니 파라미터에 `RuntimeVisibleParameterAnnotations`가 없어 Spring이 `@Qualifier`를 전혀 못 봄 — #46이 코드는 추가됐지만 실제로는 안 고쳐진 상태였음. 유일한 unit test(`CollectorClientTest`)가 스프링 컨텍스트 없이 생성자를 직접 호출해서 통과했기 때문에 놓쳤음.

## Attempt 2 — 2026-07-19  ✅ PASS
- 시도: 프로젝트 루트에 `lombok.config` 추가(`lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier`) — 개별 클래스 수동 생성자가 아니라 메커니즘 자체를 고쳐, 앞으로 `@RequiredArgsConstructor` + `@Qualifier` 조합을 쓰는 모든 곳에 적용되도록 함.
- 결과: `./gradlew compileJava --rerun` 후 `javap -v -p`로 생성자 파라미터에 `@Qualifier("collectorRestClient")`가 실제로 붙는 것 확인(`RuntimeVisibleParameterAnnotations` 확인). `./gradlew test --tests "*CollectorClientTest*"` 2건 PASS(회귀 없음). `DB_USERNAME/DB_PASSWORD/DB_HOST/DB_PORT/DB_NAME` env를 docker MySQL(3307, root)에 맞춰 설정한 뒤 `CollectorEventConsumerTest`(전체 Spring 컨텍스트 + 임베디드 Kafka)까지 PASS — 실제 빈 주입 경로에서 `@Qualifier`가 동작함을 확인.
- 검증 레벨: Level 1(단위) + Level 4(통합, 전체 컨텍스트) PASS.
