# 001-foundation — 프로젝트 뼈대 + 인프라 (로그)

## Attempt 1 — 2026-07-14

- 시도: `build.gradle`(SB 4.1.0, Java17 소스/JDK21 런타임, Gradle 8.14.2) + 패키지 뼈대(`common.response.ApiResponse`, `common.exception.*`) + `docker-compose.yml`(MySQL·Redis·Kafka KRaft) + `application.yml`/`.env.example` 작성. gradle wrapper는 Homebrew로 임시 설치한 Gradle 9.6.1로 부트스트랩.
- 결과: `./gradlew compileJava` PASS, `./gradlew test --tests "com.coffeeorder.common.*"` PASS(3 테스트), `./gradlew build` PASS.
- 검증 레벨: **Level 1(빌드+단위테스트) PASS**.
- 증거: `BUILD SUCCESSFUL` (compileJava / test / build 각각).
- 진행 중: Level 5(로컬 기동, `docker compose up` 후 앱 연결) — 로컬 Docker 데몬 재시작 중, 아래 Attempt에 이어서 기록.

## Attempt 2 — 2026-07-14  ✅ PASS

- 시도: Docker Desktop 복구(daemon 500 에러 → 강제 종료 후 재시작으로 해결) 후 `docker compose up -d`로 MySQL·Redis·Kafka 기동. 호스트에 이미 네이티브 MySQL(포트 3306, 무관한 별도 서비스)이 떠 있어 컨테이너 MySQL은 `${DB_PORT:-3307}:3306`으로 매핑하도록 `docker-compose.yml`/`application.yml`/`.env.example` 수정. `DB_USERNAME=root`/`DB_PASSWORD`(compose의 `MYSQL_ROOT_PASSWORD`)로 로컬 `./gradlew bootRun` 실행.
- 결과: MySQL(`mysqladmin ping` OK) · Redis(`PONG`) · Kafka(브로커 STARTED 로그) 모두 정상, 앱이 1.5초 만에 기동하며 HikariCP가 실제 MySQL에 연결(JDBC URL `jdbc:mysql://localhost:3307/coffee_order`)됨.
- **버그 발견 및 수정**: 실제 HTTP 요청(`GET /nonexistent`)이 정상 404가 아니라 `500 INTERNAL_ERROR`로 응답 — Spring Boot 4의 `NoResourceFoundException`이 catch-all `@ExceptionHandler(Exception.class)`에 걸려 오분류됐고, 그 핸들러엔 로깅도 없어 원인 추적이 불가능했음. `GlobalExceptionHandler`에 `NoResourceFoundException` 전용 핸들러(404) 추가 + catch-all에 `log.error` 추가로 수정.
- 검증 레벨: **Level 1 PASS**(수정 후 재실행) · **Level 5(로컬 기동) PASS** · **Level 6(실제 HTTP) PASS**.
- 증거(API 샘플):
  - `GET /nonexistent` → 수정 전: `500 {"code":"INTERNAL_ERROR",...}` / 수정 후: `404 {"code":"NOT_FOUND","data":null,"message":"요청한 경로를 찾을 수 없습니다"}`
  - 앱 로그: `Started CoffeeOrderSystemApplication in 1.526 seconds`, HikariCP `Added connection com.mysql.cj.jdbc.ConnectionImpl@...`
- 완료 후 `docker compose down`으로 컨테이너 정리(로컬 상태 원복).
