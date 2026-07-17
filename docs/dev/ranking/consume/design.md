# ranking/consume — Design

## 개요
주문 완료 후 발행되는 `OrderCompletedEvent`(`order-completed` 토픽)를 소비해, 인기 메뉴 집계를 위한 일자별 주문 카운트를 Redis ZSET에 누적한다(발제 4). 조회 API(최근 7일 상위 3개)는 이 이슈 범위 밖([#8](https://github.com/yxejxnn/coffee-order-system/issues/8))이며, 이 이슈는 정확한(멱등) 집계만 다룬다.

## 연동 인터페이스
- **입력**: Kafka `order-completed` 토픽, 컨슈머 그룹 `ranking-group`. [#6](https://github.com/yxejxnn/coffee-order-system/issues/6)의 `collector-group`과 같은 토픽을 독립적으로 구독한다(Kafka pub-sub 팬아웃).
- **출력**: Redis
  - 멱등 표식 키 `ranking:idempotency:{orderGroupId}` — `SETNX`(`opsForValue().setIfAbsent`) + TTL 7일. 이미 존재하면(중복 전달) 집계를 스킵한다.
  - 일자 버킷 ZSET `menu:ranking:{yyyy-MM-dd}` — 멤버는 `menuId`(문자열), `ZINCRBY 1`. 증가 후 매번 TTL 8일을 재설정한다(7일 롤링 윈도우 + 1일 버퍼, 그날 마지막 주문 시각 기준 +8일로 자연 수렴).

## 멱등 설계 — ADR-003의 "Redis SET" 구체화
[ADR-003](../../../adr/ADR-003-인기메뉴-집계전략.md)은 "order_group_id를 Redis SET에 넣고 있으면 skip"이라고만 서술한다. 이를 멤버 하나짜리 거대 `SADD` 집합으로 구현하면 멤버 단위 TTL이 불가능해 무한 성장한다. 대신 `order_group_id`마다 **개별 키**(`SET key 1 NX EX 604800`)로 만들었다 — 원자적 check-and-set이라 동시 중복 전달에도 정확히 한 번만 "처음"으로 인정되고, 키 자체 TTL로 별도 정리 없이 자연 만료된다. ADR의 의도(멱등 SET + TTL 관리)를 실제 구현 프리미티브로 옮긴 것이며 결정을 뒤집은 게 아니다.

**멱등 마킹(SETNX) 이후 집계(ZINCRBY+EXPIRE) 실패 시 롤백**: 두 단계가 원자적이지 않으므로, 마킹 성공 후 집계가 실패하면(Redis 네트워크 오류 등) 마킹만 롤백(`DELETE`)하고 예외를 다시 던진다. 롤백 없이 두면 Kafka 재전달 시 이미 마킹된 키 때문에 "중복"으로 오판해 그 주문이 영구적으로 누락되는데, 이는 멱등이 막으려는 중복 집계보다 나쁜 실패 모드다. 롤백해도 마킹 성공 직후~롤백 사이의 하드 크래시(예외로 잡히지 않는 프로세스 종료) 구간은 여전히 남는데, 이 잔여 위험은 Lua 스크립트로 SETNX+ZINCRBY+EXPIRE를 완전히 원자화해야 없앨 수 있다 — 이 프로젝트에 Lua 스크립팅 선례가 없어 이번 이슈에서는 도입하지 않고, 위 롤백으로 실패 모드를 완화하는 선에서 마무리했다(자체 리뷰에서 발견, PR #28).

## 실패 정책
멱등 스킵(중복 이벤트)은 실패가 아니라 정상 경로다 — INFO 로그만 남기고 정상 반환, 예외를 던지지 않으므로 Kafka 오프셋은 정상 커밋된다. Redis 자체 장애(연결 실패 등)에 대한 재시도·서킷브레이커는 이 이슈 범위 밖으로 뒀다 — [#6](../../collector/consume/design.md)의 외부 HTTP 전송과 달리 Redis는 애플리케이션과 같은 docker-compose 스택 내부 인프라라 별도 재시도 정책 없이 컨슈머 예외를 그대로 전파하도록 뒀다(Kafka가 오프셋 미커밋으로 자동 재시도). 단, 무한정 블로킹은 막기 위해 `spring.data.redis.timeout`을 3초로 설정했다(collector의 `RestClient` 타임아웃과 같은 취지).

## 알려진 한계 (자체 리뷰에서 발견, 이 이슈 범위 밖으로 남김)
- **일자 버킷은 소비 시각 기준**: `RankingAggregationService`는 `LocalDate.now(Asia/Seoul)`로 버킷을 정하는데, 이는 주문 시각이 아니라 컨슈머가 그 이벤트를 처리한 시각이다. `OrderCompletedEvent`에 타임스탬프 필드가 없어 주문 시각 기준으로 바꾸려면 이벤트 스키마 변경(및 [#6](../../collector/consume/design.md) 소비자에도 영향)이 필요해 이번 이슈 범위를 넘는다. 컨슈머가 몇 시간 이상 지연되거나 오프셋이 복구 목적으로 리셋돼 자정을 넘겨 재처리되면 그날 버킷이 실제 주문일과 어긋날 수 있다 — 과제/데모 환경에서는 영향이 제한적이라 판단해 보류했다.
- **멱등 TTL(7일)은 7일보다 오래된 재생(replay)을 막지 못한다**: `ranking-group`의 오프셋을 수동으로 리셋해 과거 데이터를 재처리하면 이미 만료된 멱등 키 때문에 중복 집계될 수 있다. ADR-003은 이런 복구를 Kafka 재생이 아니라 `ORDERS` 테이블 기반 DB 재구축 쿼리로 하도록 이미 설계해뒀으므로(재생이 아닌 재구축 경로), 실무적으로는 이 경로를 타지 않는 것을 전제로 한다.

## 관련 코드 위치
- `com.coffeeorder.domain.ranking.consumer.RankingEventConsumer` — `@KafkaListener`.
- `com.coffeeorder.domain.ranking.service.RankingAggregationService` — 멱등 체크 + `ZINCRBY`.
- `com.coffeeorder.domain.ranking.RankingRedisKeys` — Redis 키 포맷 상수([#8](https://github.com/yxejxnn/coffee-order-system/issues/8)에서 `rankingKey` 재사용 예정).
- `application.yml`의 `spring.data.redis.*`(기존 [#1](../../setup/foundation/design.md) 설정 재사용, 이 이슈에서 최초로 실사용).

## 테스트
- `RankingEventConsumerTest` — `@EmbeddedKafka` + `@MockitoBean`으로 배선만 검증(`CollectorEventConsumerTest`와 동일 패턴).
- `RankingAggregationServiceTest` — 실제 Redis(`docker compose`의 `redis`)에 대해 최초 증가·동일 `orderGroupId` 2회 전달 시 1만 증가(이슈 완료 기준)를 직접 검증. `tearDown()`은 이 테스트가 쓴 menuId만 `ZREM`으로 제거한다(같은 Redis를 공유하는 실사용/데모 데이터를 건드리지 않기 위함 — 자체 리뷰에서 발견, PR #28).
- `RankingAggregationServiceConcurrencyTest` — `PointServiceConcurrencyTest` 패턴으로 (a) 서로 다른 `orderGroupId` N개 동시 처리 시 최종 점수 N(멀티 인스턴스 동시 증가 근사), (b) 같은 `orderGroupId` 동시 처리 시 점수 1만 증가(레이스에서도 멱등).
- `RankingAggregationServiceUnitTest` — Mockito로 `StringRedisTemplate`을 모킹해, 멱등 마킹 후 집계가 실패하면 마킹이 롤백되는지(위 "멱등 마킹 이후 집계 실패 시 롤백" 참고)를 실제 Redis 장애 없이 결정적으로 검증한다.
