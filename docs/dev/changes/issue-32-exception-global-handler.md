# 잘못된 요청 본문/타입 불일치 → 500 오응답 수정

대상: exception/global-handler
이슈: #32
담당: Claude

## 배경 / 요구
자체 리뷰(코드 감사)에서 발견: `GlobalExceptionHandler`에 `HttpMessageNotReadableException`(JSON 파싱 실패), `MethodArgumentTypeMismatchException`(파라미터 타입 불일치) 핸들러가 없어 catch-all `Exception` 핸들러로 떨어져 500 + `log.error("처리되지 않은 예외 발생")`가 발생하던 문제.

## 설계
두 예외를 기존 `NoResourceFoundException` 핸들러와 동일한 패턴(`ApiResponse.error(ErrorCode)`)으로 `INVALID_INPUT`(400)에 매핑.

## 관련 결정·질문
없음 (기존 컨벤션을 그대로 따르는 단순 수정)

## 태스크
- [x] `GlobalExceptionHandler`에 두 핸들러 추가
- [x] `GlobalExceptionHandlerTest`에 회귀 테스트 2건 추가
- [x] design.md 작성

## 평가(통과) 기준
- `./gradlew test --tests "*GlobalExceptionHandlerTest*"` 통과
- 검증 레벨: Level 1(단위) — 컨트롤러를 거치지 않고 핸들러 메서드를 직접 호출하는 유닛 테스트로 충분(입출력이 순수 매핑이라 실제 HTTP 왕복이 필요한 로직 없음)

> 참고: 이 이슈는 진행 중 `docs/dev/ongoing/`에 별도 문서를 만들지 않고 바로 완료 처리됐다 — 규모가 작아 생략. 다음부터는 이슈를 집는 시점에 ongoing 문서를 먼저 만든다.
