# 001-popular — 인기 메뉴 조회 API (로그)

## Attempt 1 — 2026-07-17  ✅ PASS
- 시도: `com.coffeeorder.domain.ranking` 패키지에 `RankingQueryService`(Redis `unionWithScores`로 최근 7일 일자 버킷 union → score desc·menuId asc 정렬·상위 3 → `MenuRepository.findAllById`로 이름 조인) · `RankingController`(`GET /api/menus/popular`) · `PopularMenuResponse`(record) 구현. `RankingRedisKeys`에 `RANKING_ZONE`(기존 `RankingAggregationService`의 private 상수를 이동, 집계/조회 양쪽이 "오늘"을 같은 타임존 기준으로 계산하도록 통합) · `recentRankingKeys(LocalDate)`(최근 7일 키 목록) 추가.
- 결과: `./gradlew test --tests "*ranking*"` 신규 8개(유닛 4 + 통합 2 + 컨트롤러 2) 포함 전체 통과(`BUILD SUCCESSFUL`), `./gradlew test` 전체 회귀(56개) 통과.
- 검증 레벨: Level 1(단위, `RankingQueryServiceUnitTest`) PASS · Level 2(Controller 계약, `RankingControllerTest`) PASS · Level 3~4(실제 Redis union·DB 조인, `RankingQueryServiceTest`) PASS · Level 6(실제 HTTP) PASS.
- 증거:
  - `RankingQueryServiceUnitTest`: union이 `null`이면 빈 리스트, 정확히 7개 최근 일자 키로 union 호출(7일 경계 로직의 키 구성 검증), score desc→menuId asc 정렬 후 상위 3 제한, 2개만 있을 때 패딩 없이 2개 반환.
  - `RankingQueryServiceTest`(실제 Redis+MySQL): 실제 `ZSET` union + 실제 `Menu` 조인으로 이름·카운트 확인. **7일 경계**: `today.minusDays(6)` 버킷은 결과에 포함, `today.minusDays(7)` 버킷은 결과에서 완전히 제외됨을 확인(공유 Redis 오염과 무관하게 결정적인 검증).
  - Level 6 — 로컬 `bootRun`(`docker compose`의 `coffee-order-mysql`(3307)·`coffee-order-redis`(6379)·`coffee-order-kafka` 사용) 기동 후:
    - `GET /api/menus/popular` → `200 {"code":"SUCCESS","data":[{"rank":1,"menuId":75,"name":"아메리카노","orderCount":5},{"rank":2,"menuId":85,"name":"아메리카노","orderCount":5},{"rank":3,"menuId":3,"name":"아메리카노","orderCount":2}]}` — 실제 응답에서 동점(menuId 75·85 둘 다 orderCount 5)일 때 `menuId` 오름차순(75 먼저)으로 정렬됨을 확인.
  - 실행 환경: `DB_HOST=localhost DB_PORT=3307 DB_USERNAME=root DB_PASSWORD=changeme_local_only DB_NAME=coffee_order`.
