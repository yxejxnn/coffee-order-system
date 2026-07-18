# 주문 요청 멱등성 부여

대상: order/create
이슈: [#37](https://github.com/yxejxnn/coffee-order-system/issues/37)
담당: Claude

## 배경 / 요구
`orderGroupId`를 서버가 생성해서, 클라이언트가 타임아웃 후 같은 주문을 재시도하면 새 주문 + 포인트 이중 차감이 발생한다.

## 설계
`Idempotency-Key` 헤더(선택, 하위호환 유지) + `orders.idempotency_key` 유니크 제약. 헤더가 오면 먼저 기존 주문을 조회해 있으면 결제·검증을 건너뛰고 그 결과를 그대로 반환. 최소 스코프로: body 일치 검증 없음, 동시 동일 키 요청의 극히 좁은 경합(둘 다 "없음" 관찰 후 하나만 유니크 제약 통과)은 트랜잭션 전체 롤백으로 이중 차감은 막되 그 요청 자체는 실패로 남을 수 있음을 문서화만 하고 안 고침.

## 관련 결정·질문
- `docs/dev/order/create/design.md` (갱신 필요)

## 태스크
- [x] `Order` 엔티티에 `idempotencyKey` 컬럼/생성자 인자 추가
- [x] `OrderRepository#findByIdempotencyKey`
- [x] `OrderController`에 `Idempotency-Key` 헤더 추가
- [x] `OrderService.create`에 조회-후-단락 로직 추가
- [x] `PointService#getBalance`(비잠금) 신설, 재조회 응답용
- [x] `docs/api/order.md`·`docs/db/orders.md` 갱신
- [x] 테스트 추가/보정 + 실제 HTTP(Level 6) 검증

## 평가(통과) 기준
- 같은 `Idempotency-Key`로 2번 호출 시 두 번째는 결제·저장 없이 첫 결과 그대로 반환 — 단위 테스트 + 실제 HTTP로 확인 완료(`docs/logs/order/create/003-idempotency.md`)
- 검증 레벨: Level 1(단위+회귀)·Level 2(컨트롤러 계약)·Level 6(실제 HTTP) PASS
