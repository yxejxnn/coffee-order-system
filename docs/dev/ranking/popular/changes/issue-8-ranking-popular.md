# 인기 메뉴 조회 API

대상: ranking/popular
이슈: [#8](https://github.com/yxejxnn/coffee-order-system/issues/8)

## 배경 / 요구
발제 4번 — 최근 7일 인기 메뉴 3개 조회.

## 설계 (HOW)
- `com.coffeeorder.domain.ranking` 패키지에 조회 전용 컴포넌트 추가(#7의 집계 컴포넌트와 분리):
  - `RankingQueryService`(`service/`): `LocalDate.now(RankingRedisKeys.RANKING_ZONE)` 기준 `RankingRedisKeys.recentRankingKeys(today)`(최근 7일, 오늘 포함)로 7개 일자 키를 만들고, `StringRedisTemplate.opsForZSet().unionWithScores(첫키, 나머지6개)`로 실제 Redis `ZUNION`(대상 키에 쓰지 않는 read-only 버전, Redis 7-alpine + Spring Data Redis 4.1.0에서 지원 확인)을 호출. 결과를 score 내림차순 → 동점이면 menuId 오름차순으로 정렬해 상위 3개만 취하고, `MenuRepository.findAllById(...)` 한 번으로 이름을 조인(N+1 방지). 메뉴는 등록 API가 없는 시드 전용 데이터(`DataSeeder`)라 삭제될 수 없으므로 존재 검증 없이 바로 사용.
  - `RankingController`(`controller/`): `GET /api/menus/popular` → `ApiResponse.ok(rankingQueryService.getPopularMenus())`.
  - `PopularMenuResponse`(`dto/`, record): `rank`·`menuId`·`name`·`orderCount`. 엔티티 단건 변환이 아니라 서비스가 여러 값을 조립하는 응답이라 `from` 정적 팩토리 없이 직접 생성.
- `RankingRedisKeys` 보강: `RankingAggregationService`(#7)에 private로 있던 `ZoneId RANKING_ZONE`을 `public static final`로 이동 — 집계(write)·조회(read) 양쪽이 "오늘"을 반드시 같은 타임존 기준으로 계산해야 날짜 버킷 경계가 어긋나지 않으므로 한 곳(키 포맷을 이미 캡슐화한 클래스)으로 합침. `recentRankingKeys(LocalDate today)` 추가 — 최근 7일 롤링 윈도우를 키 union으로 구현하는 ADR-003 방식 그대로.
- 3개 미만/0개 케이스는 별도 분기 없이 union 결과 크기가 `.limit(3)`에 자연히 반영됨.

## 관련 결정·질문
- [`docs/api/ranking.md`](../../../../api/ranking.md) · [`docs/policy/popular-menu.md`](../../../../policy/popular-menu.md) · [`ADR-003`](../../../../adr/ADR-003-인기메뉴-집계전략.md)

## 태스크
- [x] `RankingRedisKeys` 보강(`RANKING_ZONE` 이동, `recentRankingKeys`) · `RankingQueryService` · `RankingController` · `PopularMenuResponse` 구현
- [x] `RankingQueryServiceUnitTest`(정렬·동점·3개 미만·7일 키 구성) · `RankingQueryServiceTest`(실제 Redis+DB, 7일 경계) · `RankingControllerTest` 작성 및 통과
- [x] Level 6(실제 HTTP) 검증, `docs/logs/ranking/popular/001-popular.md`에 기록

## 평가(통과) 기준
- 7일 경계·union·정렬·3개 미만 케이스(Level 3~4), 실제 HTTP(**Level 6**) — 모두 확인 완료. 상세 근거는 `docs/logs/ranking/popular/001-popular.md` 참고.
