# order/create 검증 정리

대상: order/create
이슈: [#35](https://github.com/yxejxnn/coffee-order-system/issues/35), [#42](https://github.com/yxejxnn/coffee-order-system/issues/42) (같은 PR로 묶어 처리)
담당: Claude

## 배경 / 요구
- #35: `OrderService.create()`가 메뉴 조회 후 quantity를 검증해서, menuId·quantity가 둘 다 무효면 진짜 원인(quantity)이 가려짐.
- #42: `memberRepository.existsById`가 `OrderService`·`PointService` 양쪽에 있다는 지적.

## 설계
- #35: quantity 계산·검증을 메서드 맨 앞으로 이동(DB 호출 전). `docs/api/order.md`엔 기본값(1)이 이미 문서화돼 있어 손댈 것 없음.
- #42: 조사 결과 `PointService.ensurePointCreated`의 체크는 `PointController.charge()`(사전 검증 없는 진입점) 정확성에 필수라 제거 불가. `OrderService`쪽을 없애면 에러 우선순위 역전 + 불필요한 메뉴 조회라는 실질적 회귀가 생겨 코드 변경 없이 close.

## 관련 결정·질문
없음.

## 태스크
- [x] `OrderService.create()` 검증 순서 재배치
- [x] `OrderServiceTest`의 불필요해진 스텁 제거 + 우선순위 회귀 테스트 추가
- [x] 이슈 #42 조사 결과를 코멘트로 남기고 close

## 평가(통과) 기준
- `./gradlew test --tests "*OrderServiceTest*"` PASS
- 검증 레벨: Level 1(단위+회귀)
