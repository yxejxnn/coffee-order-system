# 001-hardening — @Profile 가드 + ApiResponse record 전환 (로그)

## Attempt 1 — 2026-07-19  ✅ PASS
- 시도: `MockCollectorController`, `DataSeeder`에 `@Profile("!prod")` 추가(#40). `ApiResponse`를 record로 전환하고 `error(String, String)` 미사용 오버로드 제거(#43), 접근자 변경(`getCode()→code()` 등)에 맞춰 `ApiResponseTest`(미사용 오버로드 테스트 삭제)·`GlobalExceptionHandlerTest`·`OrderControllerTest`·`PointControllerTest`·`RankingControllerTest`·`MenuControllerTest` 6개 파일 갱신.
- 결과: `./gradlew test --tests "*ApiResponseTest*" --tests "*GlobalExceptionHandlerTest*" --tests "*OrderControllerTest*" --tests "*RankingControllerTest*" --tests "*PointControllerTest*" --tests "*MenuControllerTest*" --tests "*DataSeederTest*"` 전부 PASS(`ApiResponseTest`는 계획대로 5→4건). `./gradlew compileJava compileTestJava` PASS.
- 검증 레벨: Level 1(단위, 위 7개 클래스 재사용) PASS. `@Profile` 가드는 `DataSeederTest`가 Spring 컨텍스트 없이 생성자를 직접 호출하는 구조라 영향 없음을 확인, `MockCollectorController`를 실제로 로드하는 통합 테스트가 `prod` 프로파일을 켜지 않으므로 회귀 없음.
