# 프로젝트 뼈대 + 인프라(Docker Compose)

대상: setup/foundation
이슈: [#1](https://github.com/yxejxnn/coffee-order-system/issues/1)

## 배경 / 요구
빌드·기동되는 최소 프로젝트와 로컬 인프라를 세운다. 이후 모든 이슈의 토대.

## 설계 (HOW)
- `build.gradle`: Gradle Groovy, `org.springframework.boot` 4.1.0 + `io.spring.dependency-management` 1.1.7, `sourceCompatibility`/`targetCompatibility` = 17 (toolchain 미사용 — 로컬 기본 JDK 21로 컴파일·실행, `--release 17`). 의존성: web, validation, data-jpa, data-redis, spring-kafka, mysql-connector-j(runtime), lombok.
- 패키지: `com.coffeeorder` 루트, `common.response.ApiResponse<T>`(성공/에러 정적 팩토리), `common.exception`(`ErrorCode` enum, `CoffeeOrderException`, `GlobalExceptionHandler` — `@RestControllerAdvice`가 `CoffeeOrderException`·`MethodArgumentNotValidException`·`Exception`을 `ApiResponse`로 매핑).
- `docker-compose.yml`: `mysql:8.0`, `redis:7-alpine`, `apache/kafka:3.8.0`(KRaft 단일 브로커, broker+controller 결합 모드). `.env`(gitignore)에 `MYSQL_ROOT_PASSWORD`·`MYSQL_DATABASE`, `.env.example`(트래킹)로 템플릿 제공.
- `application.yml`: `DB_USERNAME`/`DB_PASSWORD` 기본값 없음(fail-fast), 나머지 host/port는 로컬 기본값(`${VAR:default}`). MySQL 호스트 포트는 `3307`(로컬 머신에 이미 네이티브 MySQL이 3306을 쓰고 있어 충돌 회피).
- gradle wrapper는 로컬에 Homebrew로 임시 설치한 Gradle 9.6.1을 이용해 8.14.2 버전으로 부트스트랩(1회성, 이후 `./gradlew`만 사용).
- `GlobalExceptionHandler`에 `NoResourceFoundException`(Spring Boot 4의 미매핑 경로 예외) 전용 404 핸들러 추가 + catch-all `Exception` 핸들러에 `log.error` 추가(실제 기동 검증 중 발견한 오분류·무로깅 버그 수정, `docs/logs/setup/foundation/001-foundation.md` Attempt 2 참고).

## 관련 결정·질문
- `docs/code-convention.md` (계층·응답 래퍼·DI 규약)
- 크리덴셜 fail-fast 규약(트래킹되는 application.yml에 기본값 없음)

## 태스크
- [x] build.gradle / settings.gradle / gradle wrapper
- [x] `CoffeeOrderSystemApplication`, `ApiResponse<T>`, `ErrorCode`, `CoffeeOrderException`, `GlobalExceptionHandler`
- [x] `application.yml`, `docker-compose.yml`, `.env.example`, `.gitignore`(`.env` 추가)
- [x] `ApiResponse`/`GlobalExceptionHandler` 단위 테스트
- [x] `./gradlew build` — Level 1 PASS
- [x] `docker compose up` 후 앱 기동, MySQL·Redis·Kafka 연결 확인 — Level 5 PASS
- [x] 실제 HTTP 요청(`curl`)으로 정상 응답·404 처리 확인 — Level 6 PASS

## 평가(통과) 기준
- `./gradlew build` 성공, `docker compose up` 후 앱이 3인프라에 연결 기동 — **Level 5(로컬 기동)**.
- `ApiResponse`/전역 예외 핸들러 단위 테스트.
