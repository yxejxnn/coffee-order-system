# 001-global-handler — 잘못된 요청 본문/타입 불일치 → 500 오응답 수정 (로그)

## Attempt 1 — 2026-07-18  ✅ PASS
- 시도: `GlobalExceptionHandler`에 `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` 핸들러 추가, 기존 `NoResourceFoundException` 핸들러와 동일 패턴으로 `INVALID_INPUT`(400) 매핑. `GlobalExceptionHandlerTest`에 대응 테스트 2건 추가.
- 결과: `./gradlew test --tests "*GlobalExceptionHandlerTest*"` 전부 PASS(신규 2건 포함). 전체 `./gradlew test`는 59건 중 43건 PASS·16건 실패이나, 실패한 9개 클래스(`CollectorEventConsumerTest`, `RankingQueryServiceTest`, `PointServiceConcurrencyTest` 등)는 전부 임베디드 Kafka/Redis/Testcontainers가 필요한 통합 테스트로 로컬 Docker 미기동에 따른 환경 이슈 — 본 변경과 무관함을 실패 클래스 목록으로 확인.
- 검증 레벨: Level 1(단위) PASS. 컨트롤러를 거치지 않고 핸들러 메서드를 직접 호출하는 순수 매핑 테스트라 실제 HTTP 왕복(Level 6)은 불필요하다고 판단.
- 자체 리뷰(`/code-review --comment`): 발견사항 0건 — 기존 핸들러 패턴을 그대로 따른 12줄짜리 boilerplate라 리스크 낮음으로 판단, 라운드 1에서 통과.
