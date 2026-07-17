# 001-consume — 인기 메뉴 집계 컨슈머 (로그)

## Attempt 1 — 2026-07-17  ✅ PASS
- 시도: `com.coffeeorder.domain.ranking` 패키지에 `RankingRedisKeys`(키 포맷 상수) · `RankingAggregationService`(멱등 체크 + `ZINCRBY`) · `RankingEventConsumer`(`@KafkaListener`, `groupId=ranking-group`) 구현. 멱등은 ADR-003의 "Redis SET" 문구를 `order_group_id`별 개별 키(`SETNX`+TTL 7일)로 구체화, 일자 버킷 ZSET은 TTL 8일로 자연 만료.
- 결과: `./gradlew test --tests "*Ranking*"` 5개 테스트 전체 통과(`BUILD SUCCESSFUL`), `./gradlew test` 전체 회귀도 통과.
- 검증 레벨: Level 1(단위) PASS · Level 3(Redis 원자 연산 기반 동시성) PASS · Level 4(실제 Redis·EmbeddedKafka 외부 인프라 통합) PASS.
- 증거:
  - `RankingEventConsumerTest`: `order-completed` 토픽에 이벤트 발행 → `RankingAggregationService.aggregate(event)` 호출 검증(배선 확인).
  - `RankingAggregationServiceTest`: 최초 이벤트 → `ZSCORE menu:ranking:{오늘} {menuId}` == 1.0. 같은 `orderGroupId` 2회 `aggregate()` 호출 → 점수 여전히 1.0(완료 기준 직접 검증).
  - `RankingAggregationServiceConcurrencyTest`: 서로 다른 `orderGroupId` 30개 동시 처리 → 최종 점수 30.0(멀티 인스턴스 동시 증가 근사). 같은 `orderGroupId`를 30개 스레드가 동시 처리 → 점수 1.0(레이스에서도 멱등 유지).
  - 테스트 실행 환경: `docker compose`의 `coffee-order-mysql`(3307)·`coffee-order-redis`(6379)·`coffee-order-kafka` 기존 기동 컨테이너 사용, `DB_HOST=localhost DB_PORT=3307 DB_USERNAME=root DB_PASSWORD=changeme_local_only DB_NAME=coffee_order` 환경변수로 실행.
