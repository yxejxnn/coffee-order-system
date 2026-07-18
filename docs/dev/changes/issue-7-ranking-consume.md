# 인기 메뉴 집계 컨슈머 (Redis ZSET + 멱등)

대상: ranking/consume
이슈: [#7](https://github.com/yxejxnn/coffee-order-system/issues/7)

## 배경 / 요구
발제 4번 집계 파이프라인 — 정확한 카운트(멱등)로 Redis에 적재.

## 설계 (HOW)
- 이벤트 소비 그룹 (b): `com.coffeeorder.domain.ranking.consumer.RankingEventConsumer`(`groupId=ranking-group`)가 얇게 위임, `RankingAggregationService`가 멱등 체크 + `ZINCRBY` 실행.
- 멱등: `order_group_id`마다 개별 키(`ranking:idempotency:{orderGroupId}`)로 `SETNX`+TTL 7일 — ADR-003의 "Redis SET" 문구를 개별 키+TTL로 구체화(상세: [design.md](../ranking/consume/design.md)).
- 일자 버킷 ZSET `menu:ranking:{yyyy-MM-dd}`(`RankingRedisKeys`로 캡슐화, [#8](https://github.com/yxejxnn/coffee-order-system/issues/8) 재사용 예정)에 `ZINCRBY 1`, TTL 8일(7일 롤링 윈도우+버퍼).

## 관련 결정·질문
- [`ADR-003`](../../adr/ADR-003-인기메뉴-집계전략.md) · [`docs/policy/popular-menu.md`](../../policy/popular-menu.md)
- 상세 설계: [`docs/dev/ranking/consume/design.md`](../ranking/consume/design.md)

## 태스크
- [x] `RankingRedisKeys`(키 포맷 상수) · `RankingAggregationService`(멱등+집계) · `RankingEventConsumer`(`@KafkaListener`) 구현
- [x] `RankingEventConsumerTest`(배선), `RankingAggregationServiceTest`(실제 Redis, 멱등 검증), `RankingAggregationServiceConcurrencyTest`(동시성) 작성 및 통과
- [x] `design.md` 작성

## 평가(통과) 기준
- **같은 `order_group_id` 2회 전달 시 카운트 1만 증가**(멱등), 멀티 인스턴스 동시 증가 정확성 — **Level 4**.
