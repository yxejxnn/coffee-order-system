# 인기 메뉴 조회 API

대상: ranking/popular
이슈: [#8](https://github.com/yxejxnn/coffee-order-system/issues/8)

## 배경 / 요구
발제 4번 — 최근 7일 인기 메뉴 3개 조회.

## 설계 (HOW)
- `com.coffeeorder.domain.ranking` 패키지에 조회 전용 컴포넌트 추가(#7의 집계 컴포넌트와 분리):
  - `RankingQueryService`(`service/`): `LocalDate.now(RankingRedisKeys.RANKING_ZONE)` 기준 `RankingRedisKeys.recentRankingKeys(today)`(최근 7일, 오늘 포함)로 7개 일자 키를 만들고, `StringRedisTemplate.opsForZSet().unionWithScores(첫키, 나머지6개)`로 실제 Redis `ZUNION`(대상 키에 쓰지 않는 read-only 버전, Redis 7-alpine + Spring Data Redis 4.1.0에서 지원 확인)을 호출. 결과를 score 내림차순 → 동점이면 menuId 오름차순으로 정렬해 상위 3개만 취하고, `MenuRepository.findAllById(...)` 한 번으로 이름을 조인(N+1 방지). 메뉴는 등록 API가 없는 시드 전용 데이터(`DataSeeder`)라 삭제될 수 없으므로 존재 검증 없이 바로 사용.
  - `RankingController`(`controller/`): `GET /api/menus/popular` → `ApiResponse.ok(rankingQueryService.getPopularMenus())`.
  - `PopularMenuResponse`(`dto/`, record): `rank`·`menuId`·`name`·`orderCount`. `OrderCreateResponse.from(Order, Long)` 선례(엔티티+외부 계산값 조합)를 따라 `from(Integer rank, Menu menu, Long orderCount)` 정적 팩토리로 조립.
- `RankingRedisKeys` 보강: `RankingAggregationService`(#7)에 private로 있던 `ZoneId RANKING_ZONE`을 `public static final`로 이동 — 집계(write)·조회(read) 양쪽이 "오늘"을 반드시 같은 타임존 기준으로 계산해야 날짜 버킷 경계가 어긋나지 않으므로 한 곳(키 포맷을 이미 캡슐화한 클래스)으로 합침. `recentRankingKeys(LocalDate today)` 추가 — 최근 7일 롤링 윈도우를 키 union으로 구현하는 ADR-003 방식 그대로.
- 3개 미만/0개 케이스는 별도 분기 없이 union 결과 크기가 `.limit(3)`에 자연히 반영됨.
- 랭킹 ZSET에는 있으나 `MenuRepository`에서 조회되지 않는 menuId(메뉴가 DB에서 사라진 경우)는 경고 로그를 남기고 스킵 — 8일 TTL인 랭킹 버킷과 메뉴 테이블이 독립적으로 진화할 수 있어, 초기 판단("메뉴는 삭제될 수 없어 존재 검증 불필요")을 자체 리뷰에서 뒤집음(상세: 자체 리뷰 절 참고).

## 자체 리뷰(`/code-review --comment`, high effort, 8각도) 반영
- **수정**: (1) 랭킹에는 있으나 DB에서 사라진 menuId로 인한 NPE — 위 스킵 로직 추가(4개 각도 독립 확인, CONFIRMED). (2) 정렬 비교자에만 있던 null-score 방어를 응답 조립부와 불일치하게 방치 — Redis ZUNION은 존재하는 멤버의 score가 null일 수 없어 가드 자체를 제거해 일관성 확보(3개 각도 확인, CONFIRMED). (3) `Long.parseLong` 3중 중복 — 튜플당 한 번만 파싱하는 private record(`RankedMenu`)로 리팩터링. (4) `PopularMenuResponse`가 `from` 팩토리 없이 서비스에서 직접 조립되던 것 — `OrderCreateResponse.from(Order, Long)` 선례를 확인하고 `from` 팩토리 추가(reuse 각도, CONFIRMED. 최초 "엔티티 단건 변환 아니므로 예외" 판단이 이 선례를 놓친 것으로 확인).
- **반영하지 않음(트레이드오프로 기록)**: Redis 측 `ZUNIONSTORE`+`ZREVRANGE`로 top-N만 가져오자는 제안(efficiency 각도, PLAUSIBLE) — 메뉴 카탈로그가 등록 API 없는 시드 전용 소규모 데이터라 이득 대비 임시 키·TTL 관리 복잡도가 안 맞다고 판단, 반영 안 함.
- 테스트 컨벤션 관련(`RankingControllerTest`의 record 생성자 한 줄 호출) 지적은 기존 `MenuControllerTest`·`OrderControllerTest`도 동일 패턴이라 선례와 일치 — 수정하지 않음.
- 신규: `RankingQueryServiceUnitTest`에 "메뉴 삭제로 조회 실패 시 스킵" 케이스 추가, `RankingQueryServiceTest`의 teardown을 index 쌍 리스트에서 `Runnable` 클린업 리스트로 정리(단순화 각도).

## 관련 결정·질문
- [`docs/api/ranking.md`](../../api/ranking.md) · [`docs/policy/popular-menu.md`](../../policy/popular-menu.md) · [`ADR-003`](../../adr/ADR-003-인기메뉴-집계전략.md)

## 태스크
- [x] `RankingRedisKeys` 보강(`RANKING_ZONE` 이동, `recentRankingKeys`) · `RankingQueryService` · `RankingController` · `PopularMenuResponse` 구현
- [x] `RankingQueryServiceUnitTest`(정렬·동점·3개 미만·7일 키 구성) · `RankingQueryServiceTest`(실제 Redis+DB, 7일 경계) · `RankingControllerTest` 작성 및 통과
- [x] Level 6(실제 HTTP) 검증, `docs/logs/ranking/popular/001-popular.md`에 기록

## 평가(통과) 기준
- 7일 경계·union·정렬·3개 미만 케이스(Level 3~4), 실제 HTTP(**Level 6**) — 모두 확인 완료. 상세 근거는 `docs/logs/ranking/popular/001-popular.md` 참고.
