# setup/foundation — Design

## 개요
Spring Boot 4.1.0(Java 17 소스 / JDK 21 런타임) 프로젝트 뼈대와 로컬 인프라(MySQL·Redis·Kafka)를 제공한다. 이후 모든 이슈가 이 위에서 구현된다.

## API / 인터페이스
이 기능 자체는 신규 엔드포인트를 노출하지 않는다. 공통 응답 포맷과 전역 예외 처리 계약만 확정:
- `ApiResponse<T>` — `{ "code": string, "message": string|null, "data": T|null }`, `@JsonInclude(NON_NULL)`로 null 필드는 응답에서 생략.
  - 성공: `ApiResponse.ok(data)` / `ApiResponse.ok()` → `code: "SUCCESS"`.
  - 실패: `ApiResponse.error(ErrorCode)` / `error(ErrorCode, message)`.
- `GlobalExceptionHandler`(`@RestControllerAdvice`)가 모든 예외를 `ApiResponse`로 매핑:
  - `CoffeeOrderException` → 해당 `ErrorCode`의 상태·코드·메시지
  - `MethodArgumentNotValidException` → `400 COMMON_001`(필드 에러 메시지 우선)
  - `NoResourceFoundException`(미매핑 경로) → `404 COMMON_003`
  - 그 외 `Exception` → `500 COMMON_002`(반드시 `log.error`로 남김)

## 데이터 모델
이 기능 범위 아님(엔티티는 이슈 #2에서 도입).

## 규칙 / 검증
- **에러 코드 체계**: `ErrorCode` enum이 `status`/`code`/`message`의 유일한 원천. 코드값은 도메인 네임스페이스(`CATEGORY_NNN`) 형식 — `COMMON_001~003`, `MEMBER_001`, `MENU_001`, `POINT_001~002`, `ORDER_001`. 새 에러 유형을 추가할 때도 반드시 이 enum에 등록하고, 문자열을 직접 하드코딩하지 않는다(`docs/code-convention.md` "매직 넘버/문자열 지양 → 상수화").
- **인프라**: `docker-compose.yml`이 MySQL 8.0(호스트 포트 `${DB_PORT:-3307}`)·Redis 7·Kafka 3.8(KRaft 단일 브로커)을 제공. `.env`(gitignore)에 `MYSQL_ROOT_PASSWORD`·`MYSQL_DATABASE`·`DB_PORT`, `.env.example`이 템플릿. `.env`는 docker compose만 읽으며 Spring 앱(Gradle/IDE 실행)은 읽지 않는다 — `DB_USERNAME`/`DB_PASSWORD`는 기본값이 없어(fail-fast) IDE Run Configuration 또는 셸 환경변수로 별도 주입해야 한다.
- **로컬 개발 DB 포트**: `application.yml`의 `DB_PORT` 기본값은 `3306`(로컬 네이티브 MySQL 사용, 사용자 선택). docker-compose 경로는 `.env`의 `DB_PORT=3307`이 별도로 적용되어 서로 영향 없음.
- **DI**: 생성자 주입만 사용(`docs/code-convention.md`).
- **Lombok**: `ErrorCode`, `CoffeeOrderException`은 수동 게터 대신 `@Getter` 사용(`ErrorCode`는 `@RequiredArgsConstructor`로 생성자도 생략). `ApiResponse`는 record로 전환됐다(#43) — 접근자는 `code()`/`message()`/`data()`, Lombok 미사용.

## 관련 정책·의존
- `docs/code-convention.md` (계층·DI·Lombok·매직 스트링 규칙)
- 참고 구현: [sparta-payment-system의 ApiResponse/ErrorCode](https://github.com/yxejxnn/sparta-payment-system)
