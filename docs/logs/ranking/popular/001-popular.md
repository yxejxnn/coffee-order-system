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

## Attempt 2 — 2026-07-17  ✅ PASS (자체 리뷰 반영, PR #30)
- 시도: `/code-review --comment` 자체 리뷰(high effort, 8각도 finder + 교차 합의 기반 판단) 결과 5건 중 4건 반영.
  - `RankingQueryService`: 랭킹 ZSET에는 있으나 `MenuRepository`에서 조회 안 되는 menuId(메뉴가 DB에서 사라진 경우)에 대해 `menu.getName()`을 바로 호출해 NPE 나던 것을 스킵(경고 로그)하도록 수정 — 4개 각도가 독립적으로 확인한 가장 심각한 발견(CONFIRMED). 랭킹 버킷 TTL 8일 vs 조회 윈도우 7일이라 메뉴 삭제 후 최대 7일간 API 전체가 500이 될 수 있었음.
  - 정렬 비교자에만 있던 null-score 방어(`tuple.getScore() == null ? 0 : ...`)가 응답 조립부(`tuple.getScore().longValue()`)엔 없어 불일치하던 것을 — Redis ZUNION은 존재하는 멤버의 score가 null일 수 없음을 확인하고 가드 자체를 제거해 통일(3개 각도 확인, CONFIRMED).
  - `Long.parseLong(tuple.getValue())`이 정렬·menuIds 빌드·응답 루프 세 곳에서 중복 파싱되던 것을 private record `RankedMenu(Long menuId, Double score)`로 한 번만 파싱하도록 리팩터링.
  - `PopularMenuResponse`가 `from` 팩토리 없이 서비스에서 직접 조립되던 것을 — `OrderCreateResponse.from(Order, Long)` 선례(엔티티+외부 계산값 조합)를 뒤늦게 확인하고 `PopularMenuResponse.from(Integer, Menu, Long)` 팩토리 추가.
  - 반영하지 않은 항목(PR 코멘트로 회신, 근거는 design.md "자체 리뷰 반영" 절 참고): Redis 측 `ZUNIONSTORE`+`ZREVRANGE` top-N 제안(메뉴 카탈로그 규모 대비 복잡도 과함) · `RankingControllerTest`의 record 한 줄 생성자(기존 `MenuControllerTest`·`OrderControllerTest` 선례와 일치, 실제 위반 아님).
  - `RankingQueryServiceUnitTest`에 "메뉴 삭제로 스킵" 케이스 추가, `RankingQueryServiceTest`의 teardown을 index 쌍 리스트 → `Runnable` 클린업 리스트로 정리, 7일 경계 테스트의 점수를 공유 dev Redis 오염을 확실히 이기도록 상향(5.0 → 1,000,000.0) — 실제로 실행 중 오염 때문에 실패하는 것을 목격하고 수정.
- 결과: `./gradlew test --tests "*ranking*"` 신규 9개 포함 전체 통과, `./gradlew test` 전체 회귀(57개) 통과. 회귀 실행 중 `RankingAggregationServiceTest`가 1회 일시적으로 실패(공유 dev Redis의 잔여 데이터 때문으로 확인, 단독 재실행 시 정상 통과 — 이번 변경과 무관한 기존 공유 인프라 특성).
- 검증 레벨: Level 1(단위, 신규 케이스 포함) PASS · Level 3~4(실제 Redis+DB) PASS.
