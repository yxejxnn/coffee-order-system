# 003-idempotency — 주문 요청 멱등성 부여 (로그)

## Attempt 1 — 2026-07-19  ✅ PASS
- 시도: `Order` 엔티티에 `idempotency_key VARCHAR(100) UNIQUE, nullable` 컬럼 + 생성자 인자 추가(호출부 3곳: `OrderService`, `EntityMappingTest` 2곳 갱신). `OrderRepository#findByIdempotencyKey` 추가. `OrderController`에 `@RequestHeader(value = "Idempotency-Key", required = false)` 추가. `OrderService.create`에 quantity 검증 다음으로 "키가 있고 이미 처리된 주문이면 결제·검증 스킵하고 즉시 반환" 분기 추가. 응답용 잔액 조회를 위해 `PointService#getBalance`(비잠금, 신설) + `PointRepository#findByMemberId`(신설) 추가.
- 결과: `./gradlew test --tests "*Order*" --tests "*EntityMappingTest*"` PASS(신규 케이스 포함: `OrderServiceTest` 2건, `OrderControllerTest` 1건). 전체 `./gradlew test` 65건 전부 PASS, 회귀 없음.
- 검증 레벨: Level 1(단위+전체 회귀) PASS · Level 2(컨트롤러 계약) PASS · **Level 6(실제 HTTP, `bootRun`)** — 같은 `Idempotency-Key`로 `POST /api/orders`를 2번 호출해 두 응답의 `orderGroupId`·`balance`가 완전히 동일함을 확인, DB에서도 `orders` 행이 정확히 1개(멱등 키 저장됨)만 생성됐음을 재확인.
  ```
  POST /api/orders (Idempotency-Key: test-key-001) {"memberId":7,"menuId":3,"quantity":1}
  → 201 {"orderGroupId":"5bf18b9f-...","balance":275500}
  POST /api/orders (Idempotency-Key: test-key-001) {"memberId":7,"menuId":3,"quantity":1}  (재시도)
  → 201 {"orderGroupId":"5bf18b9f-...","balance":275500}  (동일 — 이중 차감 없음)
  ```
- 잔여 위험(최소 스코프, 안 고침): 동시에 같은 키로 2건이 동시에 "처리 안 됨"을 관찰하면 결제까지 각자 진행하다 `orders` 저장 유니크 제약에서 하나만 통과. 진 쪽은 트랜잭션 전체 롤백(이중 차감 없음)되지만 그 요청 자체는 500 실패. `docs/dev/order/create/design.md`·`docs/api/order.md`에 트레이드오프로 기록.
