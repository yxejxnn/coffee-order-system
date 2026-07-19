# 잡다한 컨벤션/하드닝 일괄

대상: collector/mock, config(DataSeeder), common/response
이슈: [#40](https://github.com/yxejxnn/coffee-order-system/issues/40), [#43](https://github.com/yxejxnn/coffee-order-system/issues/43) (서로 무관한 파일이지만 둘 다 트리비얼해서 같은 PR로 묶어 처리)
담당: Claude

## 배경 / 요구
- #40: `MockCollectorController`, `DataSeeder`가 데모/시드 용도라는 의도가 Javadoc에만 있고 `@Profile` 가드가 없어, 운영 아티팩트에 그대로 실림.
- #43: `ApiResponse`만 프로젝트 컨벤션(`docs/code-convention.md` "요청·응답 DTO — record")의 예외로 클래스+Lombok으로 남아 있었고, `error(String, String)` 오버로드는 프로덕션에서 안 쓰이는 미사용 코드.

## 설계
- #40: 두 클래스에 `@Profile("!prod")` 추가. 지금 프로젝트엔 별도 `prod` 프로파일이 없지만(#45에서 확인한 단일 환경), 나중에 생기면 자동으로 두 컴포넌트가 빠지는 선제적 안전장치.
- #43: `ApiResponse`를 record로 전환(`code`, `message`, `data` 컴포넌트, `docs/code-convention.md`의 "record는 항상 줄바꿈" 규칙대로 헤더 3줄 작성), `error(String, String)` 오버로드 제거. 접근자가 `getCode()/getMessage()/getData()` → `code()/message()/data()`로 바뀌어 6개 테스트 파일(`ApiResponseTest`, `GlobalExceptionHandlerTest`, `OrderControllerTest`, `PointControllerTest`, `RankingControllerTest`, `MenuControllerTest`)의 호출부를 갱신. `ErrorCode.XXX.getMessage()`(Lombok enum, 그대로 유지)와 혼동하지 않도록 `ApiResponse` 인스턴스 쪽만 정확히 골라 수정.

## 관련 결정·질문
- 없음(둘 다 완료조건이 명확한 하드닝, 별도 트레이드오프 논쟁 없음).

## 자체 리뷰에서 발견·수정한 문제
`docs/dev/setup/foundation/design.md`가 이번 변경으로 사실과 어긋나게 됨을 발견 — ①"실패: ... `error(String code, String message)`" 문구가 이번에 제거한 오버로드를 여전히 계약처럼 문서화하고 있었고, ②"Lombok: 공통 클래스(`ApiResponse`, ...)는 ... `@Getter` 사용"도 `ApiResponse`가 record로 바뀐 지금은 틀림. 둘 다 수정(오버로드 문구 삭제, `ApiResponse`는 record·Lombok 미사용으로 갱신) — 이 프로젝트가 #36 배치에서 다뤘던 "문서가 약속한 것과 코드가 다름" 문제와 같은 범주라 그냥 넘기지 않고 고쳤다.

## 태스크
- [x] `MockCollectorController`, `DataSeeder`에 `@Profile("!prod")` 추가
- [x] `ApiResponse`를 record로 전환 + `error(String, String)` 제거
- [x] 6개 테스트 파일 접근자 호출 갱신 + `ApiResponseTest`의 미사용 오버로드 테스트 삭제
- [x] (자체 리뷰 발견) `docs/dev/setup/foundation/design.md`의 `ApiResponse` 관련 문구 갱신(제거된 오버로드·Lombok→record)

## 평가(통과) 기준
- `./gradlew test --tests "*ApiResponseTest*" --tests "*GlobalExceptionHandlerTest*" --tests "*OrderControllerTest*" --tests "*RankingControllerTest*" --tests "*PointControllerTest*" --tests "*MenuControllerTest*" --tests "*DataSeederTest*"` 전부 PASS(회귀 없음, `ApiResponseTest`는 계획대로 5→4건).
- `./gradlew compileJava compileTestJava` PASS.
- 검증 레벨: Level 1(단위, 위 7개 클래스 재사용) PASS.
