# exception/global-handler — Design

## 개요
모든 컨트롤러에서 발생하는 예외를 `GlobalExceptionHandler`(`@RestControllerAdvice`) 한 곳에서 잡아 `ApiResponse` 형태로 일관 응답한다. 클라이언트 입력 오류(400)와 서버 오류(500)를 구분해 응답 상태 코드와 로그 레벨을 다르게 가져가는 것이 핵심 규칙이다.

## API / 인터페이스
- 구현: `common/exception/GlobalExceptionHandler`
- 매핑:
  - `CoffeeOrderException` → 예외가 가진 `ErrorCode`의 상태·코드·메시지 그대로
  - `MethodArgumentNotValidException`(`@Valid` 실패) → `INVALID_INPUT`(400), 첫 필드 오류 메시지 포함
  - `HttpMessageNotReadableException`(요청 본문 JSON 파싱 실패·타입 불일치) → `INVALID_INPUT`(400)
  - `MethodArgumentTypeMismatchException`(경로/쿼리 파라미터 타입 불일치) → `INVALID_INPUT`(400)
  - `NoResourceFoundException` → `NOT_FOUND`(404)
  - 그 외 `Exception`(catch-all) → `INTERNAL_ERROR`(500) + `log.error`로 서버 로그 남김

## 규칙 / 검증
- **입력 오류와 서버 오류를 구분한다**: 클라이언트가 잘못 보낸 요청(파싱 실패, 타입 불일치, 검증 실패)은 절대 catch-all `Exception` 핸들러(500 + error 로그)로 떨어뜨리지 않는다. 새로운 클라이언트 입력 오류 유형을 발견하면 이 원칙에 따라 전용 핸들러를 추가한다.
- 커스텀 메시지가 필요 없는 경우(`HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `NoResourceFoundException`) `ApiResponse.error(ErrorCode)` 오버로드로 `ErrorCode`의 기본 메시지만 사용한다.

## 관련 문서
- `docs/logs/exception/global-handler/001-global-handler.md`
