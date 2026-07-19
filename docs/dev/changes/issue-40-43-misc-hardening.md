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

## 태스크
- [x] `MockCollectorController`, `DataSeeder`에 `@Profile("!prod")` 추가
- [x] `ApiResponse`를 record로 전환 + `error(String, String)` 제거
- [x] 6개 테스트 파일 접근자 호출 갱신 + `ApiResponseTest`의 미사용 오버로드 테스트 삭제

## 평가(통과) 기준
- `./gradlew test --tests "*ApiResponseTest*" --tests "*GlobalExceptionHandlerTest*" --tests "*OrderControllerTest*" --tests "*RankingControllerTest*" --tests "*PointControllerTest*" --tests "*MenuControllerTest*" --tests "*DataSeederTest*"` 전부 PASS(회귀 없음, `ApiResponseTest`는 계획대로 5→4건).
- `./gradlew compileJava compileTestJava` PASS.
- 검증 레벨: Level 1(단위, 위 7개 클래스 재사용) PASS.
