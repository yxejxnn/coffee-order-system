# 인기 메뉴 집계 컨슈머 (Redis ZSET + 멱등)

대상: ranking/consume
이슈: [#7](https://github.com/yxejxnn/coffee-order-system/issues/7)

## 배경 / 요구
발제 4번 집계 파이프라인 — 정확한 카운트(멱등)로 Redis에 적재.

## 설계 (HOW)
> 집을 때 작성.
- 이벤트 소비 그룹 (b): `order_group_id` 멱등 체크(Redis SET) 후 일자 버킷 ZSET `menu:ranking:{yyyy-MM-dd}`에 `ZINCRBY`.

## 관련 결정·질문
- [`ADR-003`](../../adr/ADR-003-인기메뉴-집계전략.md) · [`docs/policy/popular-menu.md`](../../policy/popular-menu.md)

## 태스크
- [ ] (집을 때 세분화)

## 평가(통과) 기준
- **같은 `order_group_id` 2회 전달 시 카운트 1만 증가**(멱등), 멀티 인스턴스 동시 증가 정확성 — **Level 4**.
