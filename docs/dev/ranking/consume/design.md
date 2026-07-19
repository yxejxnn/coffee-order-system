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
멱등 스킵(중복 이벤트)은 실패가 아니라 정상 경로다 — INFO 로그만 남기고 정상 반환, 예외를 던지지 않으므로 Kafka 오프셋은 정상 커밋된다.

**(#41) Redis 자체 장애(연결 실패 등)에 대한 재시도·DLT**: 원래는 별도 `ErrorHandler`/DLT 설정 없이 컨슈머 예외를 그대로 전파해 Spring Kafka 기본 동작(재시도 소진 후 로그만 남기고 오프셋 커밋)에 맡겨져 있었다 — `docs/policy/popular-menu.md`가 "카운트는 정확해야 한다"고 명시한 요구사항인데, 실패 정책이 코드에도 문서에도 명시돼 있지 않은 상태였다. `collector`([#6](../../collector/consume/design.md))는 외부 HTTP 전송이라 "유실 허용"을 명시적으로 결정했지만(트레이드오프이지 방치가 아님), ranking은 정확성이 요구사항이라 같은 결정을 내릴 수 없었다.

`com.coffeeorder.config.KafkaErrorHandlingConfig`에 `ranking-group` 전용 `ConcurrentKafkaListenerContainerFactory`(빈 이름 `rankingKafkaListenerContainerFactory`)를 만들어 그 안에만 `DefaultErrorHandler`(`DeadLetterPublishingRecoverer` + `FixedBackOff(500L, 2)`, 최초 시도 포함 총 3회)를 구성했다. `RankingEventConsumer`의 `@KafkaListener`에 `containerFactory = "rankingKafkaListenerContainerFactory"`를 명시해 이 팩토리를 쓰도록 지정한다.

**처음엔 `CommonErrorHandler`를 컨텍스트에 노출되는 공용 `@Bean`으로 만들어 Boot의 기본 자동구성 팩토리가 자동으로 집어쓰게 했으나(그러면 `ranking-group`·`collector-group` 둘 다에 적용됨), 자체 리뷰에서 이게 altitude 문제임이 지적됐다** — 이 정책은 ranking 도메인 고유의 요구(정확성)에서 나온 것인데, 전역 빈으로 만들면 향후 세 번째 `@KafkaListener`가 추가될 때 그 작성자가 전혀 모르는 채로 이 재시도+DLT 정책을 암묵적으로 상속받는다(`collector-group`이 지금 영향을 안 받는 것도 "CollectorClient가 예외를 항상 삼킨다"는 조건에 우연히 기댄 것일 뿐). 전용 컨테이너 팩토리로 좁혀서, 이 정책은 `RankingEventConsumer`가 `containerFactory`를 명시적으로 선택했을 때만 적용되고, `collector-group`은 Boot 기본 팩토리(및 그 기본 재시도 동작)를 그대로 쓴다.

재시도가 소진되면 이벤트는 조용히 사라지는 대신 `KafkaTopics.ORDER_COMPLETED_DLT`(`order-completed-dlt`) 토픽에 원본 그대로 보존된다 — 재처리는 별도 운영 작업(수동 재발행 등)으로 범위 밖에 둔다.

**알려진 한계(자체 리뷰에서 발견, 코드로 안 고침)**: `RankingAggregationService.aggregate()`의 `incrementScore`+`expire` 두 Redis 호출은 원자적이지 않다 — 앞쪽만 성공하고 뒤쪽에서 실패하면(또는 클라이언트 타임아웃) catch 블록이 멱등 마킹만 롤백하고 `incrementScore` 자체는 되돌리지 않아, 컨테이너 재시도가 같은 이벤트를 다시 처리하면 이론상 이중 카운팅이 가능하다. 이건 #41이 새로 만든 문제가 아니라 #7(`ranking/consume`)에서 이미 알려진 채 "Lua 스크립트로 완전히 원자화해야 없앨 수 있는데 이 저장소에 선례가 없어 보류"로 결정된 잔여 위험이다(위 "알려진 한계" 섹션 참고) — #41은 재시도 횟수를 Kafka 기본값(9회)에서 3회로 오히려 줄여 노출 구간을 약간 줄였을 뿐, 이 근본 원자성 문제 자체를 새로 만들지 않았다. 재검토 조건은 기존과 동일(Lua/파이프라이닝을 다른 곳에서도 쓰게 되거나 실측 문제가 확인되면).

DLT 토픽명에 `.`(점)을 쓰지 않고 `-dlt`(하이픈)를 쓴 이유: Kafka는 토픽명의 `.`/`_`를 메트릭·로그 디렉터리 이름에서 같은 문자로 접어버리는데, 대소문자 구분이 없는 파일시스템(macOS 기본 APFS)에서 이 때문에 실제로 임베디드 브로커가 죽는 충돌을 로컬에서 직접 재현했다(`docs/logs/ranking/consume/002-dlt.md` 참고).

단, 무한정 블로킹은 막기 위해 `spring.data.redis.timeout`을 3초로 설정했다(collector의 `RestClient` 타임아웃과 같은 취지).

## 알려진 한계 (자체 리뷰에서 발견, 재검토 조건 포함)
PR #28 자체 리뷰(`/code-review --comment`)에서 나온 9건 중 코드로 고친 2건(멱등 마킹 롤백, 테스트 tearDown 파괴적 삭제)을 뺀 나머지를 여기 모은다 — PR 스레드 답글에만 남기면 머지 후 다시 들여다볼 일이 없어 묻히므로, "왜 지금 안 고쳤고 언제 다시 봐야 하는지"를 코드와 함께 남는 문서에 적어둔다.

- **일자 버킷은 소비 시각 기준**: `RankingAggregationService`는 `LocalDate.now(Asia/Seoul)`로 버킷을 정하는데, 이는 주문 시각이 아니라 컨슈머가 그 이벤트를 처리한 시각이다. `OrderCompletedEvent`에 타임스탬프 필드가 없어 주문 시각 기준으로 바꾸려면 이벤트 스키마 변경(및 [#6](../../collector/consume/design.md) 소비자에도 영향)이 필요해 이번 이슈 범위를 넘는다. 컨슈머가 몇 시간 이상 지연되거나 오프셋이 복구 목적으로 리셋돼 자정을 넘겨 재처리되면 그날 버킷이 실제 주문일과 어긋날 수 있다 — 과제/데모 환경에서는 영향이 제한적이라 판단해 보류했다. **재검토 조건**: `OrderCompletedEvent`에 주문 시각 필드를 추가하는 다른 작업이 생기면, 그때 이 버킷 키도 주문 시각 기준으로 같이 바꾼다.
- **멱등 TTL(7일)은 7일보다 오래된 재생(replay)을 막지 못한다**: `ranking-group`의 오프셋을 수동으로 리셋해 과거 데이터를 재처리하면 이미 만료된 멱등 키 때문에 중복 집계될 수 있다. ADR-003은 이런 복구를 Kafka 재생이 아니라 `ORDERS` 테이블 기반 DB 재구축 쿼리로 하도록 이미 설계해뒀으므로(재생이 아닌 재구축 경로), 실무적으로는 이 경로를 타지 않는 것을 전제로 한다. **재검토 조건**: 실제로 `ranking-group` 오프셋을 수동 리셋해 복구를 시도할 일이 생기면, 그 전에 반드시 DB 재구축 경로를 쓰거나 리셋 전 해당 기간 멱등 키를 미리 채워둔다.
- **Redis 왕복 3회(setIfAbsent+incrementScore+expire), `expire`는 매 이벤트마다 무조건 재호출**: Lua 스크립트/파이프라인으로 묶으면 왕복도 줄고 위 멱등 마킹의 원자성 잔여 위험(하드 크래시 구간)도 같이 없어지지만, 이 저장소에 Lua 선례가 없어 이번엔 롤백 처리로만 완화했다. **재검토 조건**: 이 프로젝트에서 Lua/파이프라이닝을 다른 곳에서도 쓰게 되거나, 실측 처리량이 문제로 확인되면 그때 도입한다.
- **`RankingAggregationService.aggregate()`는 예외를 삼키지 않고 그대로 전파**: 랭킹 집계는 collector 전송과 달리 "정확한 카운트"가 요구사항이라, Redis 오류 시 예외를 삼켜 유실시키기보다 Kafka가 오프셋을 커밋하지 않고 재전달하게 두는 쪽이 맞다고 판단해 의도적으로 유지했다(고친 게 아니라 검토 후 그대로 둔 결정). 부작용으로 `order-completed`를 발행하는 다른 `@SpringBootTest`(예: `OrderCompletedEventListenerTest`)도 이제 암묵적으로 Redis 가동을 전제하게 됐는데, `AGENTS.md`가 이미 테스트 전제조건에 Redis를 명시해뒀으므로 받아들일 만한 트레이드오프로 판단했다.
- **`RankingRedisKeys`가 도메인 루트에 위치**(계층 서브패키지 없음): 계획 단계에서 명시적으로 정하고 승인받은 위치라 유지했다(검토 후 그대로 둔 결정). `KafkaTopics`가 도메인 밖(`config`)에 있는 건 컬렉터·랭킹 두 도메인이 같이 쓰는 상수라서고, `RankingRedisKeys`는 단일 도메인이라 같은 근거는 아니지만, 정적 메서드 2개짜리 클래스를 위해 서브패키지를 새로 만드는 게 오히려 과하다고 판단했다.
- **`RankingAggregationServiceTest`/`RankingAggregationServiceConcurrencyTest`가 `usedIdempotencyKeys`/`rankingKey`/`tearDown`/`newEvent` 헬퍼(~15줄)를 그대로 중복**: 이 저장소에 아직 공유 테스트 베이스 클래스 관례가 없고 지금은 2곳뿐이라 추출을 보류했다. **재검토 조건**: 3번째로 비슷한 Redis/Kafka 통합 테스트가 생기면 그때 공유 fixture로 추출한다.

## 관련 코드 위치
- `com.coffeeorder.domain.ranking.consumer.RankingEventConsumer` — `@KafkaListener`.
- `com.coffeeorder.domain.ranking.service.RankingAggregationService` — 멱등 체크 + `ZINCRBY`.
- `com.coffeeorder.domain.ranking.RankingRedisKeys` — Redis 키 포맷 상수([#8](https://github.com/yxejxnn/coffee-order-system/issues/8)에서 `rankingKey` 재사용 예정).
- `com.coffeeorder.config.KafkaErrorHandlingConfig`(#41) — `ranking-group` 전용 재시도+DLT 정책(`rankingKafkaListenerContainerFactory`). `com.coffeeorder.config.KafkaTopics.ORDER_COMPLETED_DLT`.
- `application.yml`의 `spring.data.redis.*`(기존 [#1](../../setup/foundation/design.md) 설정 재사용, 이 이슈에서 최초로 실사용).

## 테스트
- `RankingEventConsumerTest` — `@EmbeddedKafka` + `@MockitoBean`으로 배선만 검증(`CollectorEventConsumerTest`와 동일 패턴). `consume_publishesToDeadLetterTopic_afterRetriesExhausted`(#41)는 `aggregate()`가 항상 예외를 던지도록 mock해 재시도 소진 후 DLT 토픽에 원본이 도착하는지 검증.
- `RankingAggregationServiceTest` — 실제 Redis(`docker compose`의 `redis`)에 대해 최초 증가·동일 `orderGroupId` 2회 전달 시 1만 증가(이슈 완료 기준)를 직접 검증. `tearDown()`은 이 테스트가 쓴 menuId만 `ZREM`으로 제거한다(같은 Redis를 공유하는 실사용/데모 데이터를 건드리지 않기 위함 — 자체 리뷰에서 발견, PR #28).
- `RankingAggregationServiceConcurrencyTest` — `PointServiceConcurrencyTest` 패턴으로 (a) 서로 다른 `orderGroupId` N개 동시 처리 시 최종 점수 N(멀티 인스턴스 동시 증가 근사), (b) 같은 `orderGroupId` 동시 처리 시 점수 1만 증가(레이스에서도 멱등).
- `RankingAggregationServiceUnitTest` — Mockito로 `StringRedisTemplate`을 모킹해, 멱등 마킹 후 집계가 실패하면 마킹이 롤백되는지(위 "멱등 마킹 이후 집계 실패 시 롤백" 참고)를 실제 Redis 장애 없이 결정적으로 검증한다.
