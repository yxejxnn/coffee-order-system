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

## Attempt 2 — 2026-07-17  ✅ PASS (자체 리뷰 반영, PR #28)
- 시도: `/code-review --comment` 자체 리뷰(8각도 재귀 파인더 + 1표 검증) 결과 9건 중 확정 성 높은 항목 반영.
  - `RankingAggregationService`: 멱등 마킹(SETNX) 후 집계(ZINCRBY+EXPIRE)가 실패하면 마킹을 롤백(`DELETE`)하고 예외를 다시 던지도록 수정 — 이전엔 마킹 후 실패 시 Kafka 재전달이 "중복"으로 오판되어 그 주문이 영구 누락될 수 있었음(가장 심각한 발견, CONFIRMED).
  - 일자 버킷 타임존을 `Asia/Seoul`로 명시 고정(`ZoneId`) — 이전엔 JVM 기본 타임존에 암묵 의존.
  - `RankingAggregationServiceTest`/`RankingAggregationServiceConcurrencyTest`의 `tearDown()`이 그날 ZSET 키 전체를 삭제하던 것을, 테스트가 실제로 건드린 menuId만 `ZREM`으로 지우도록 수정 — 공유 Redis의 실사용/데모 데이터를 테스트가 지워버릴 수 있었음(CONFIRMED).
  - `application.yml`에 `spring.data.redis.timeout: 3000` 추가 — 이전엔 Redis 커맨드 타임아웃이 없어 응답 지연 시 컨슈머 스레드가 무한정 블로킹될 수 있었음.
  - `RankingAggregationServiceUnitTest` 신규 추가 — Mockito로 Redis 장애를 주입해 롤백 동작을 결정적으로 검증(실제 Redis로는 장애 주입이 어려움).
  - 반영하지 않은 항목(스레드 코멘트로 회신, 근거는 `design.md` "알려진 한계" 참고): 일자 버킷의 소비-시각 기준(이벤트 스키마 변경 필요, 범위 밖) · 7일 멱등 TTL의 재생 시나리오(ADR-003이 DB 재구축 경로로 이미 회피) · Redis 3회 왕복(Lua 미도입, 선례 없음) · `RankingRedisKeys` 패키지 위치(계획 단계 승인된 선택) · 테스트 헬퍼 중복(저장소에 공유 베이스 클래스 선례 없음).
- 결과: `./gradlew test --tests "*Ranking*"` 6개 테스트(신규 유닛테스트 포함) 전체 통과, `./gradlew test` 전체 회귀도 통과.
- 검증 레벨: Level 1(단위, 신규 롤백 테스트) PASS · Level 3(동시성) PASS · Level 4(실제 Redis) PASS.
