# ranking/popular — Design

## 개요
최근 7일 인기 메뉴 상위 3개를 조회한다(발제 4번). 집계(쓰기, #7 `ranking/consume`)와 조회(읽기)를 분리해, 조회는 Redis ZSET을 읽기 전용으로만 사용한다.

## API / 인터페이스
- `GET /api/menus/popular` — 상세 계약은 `docs/api/ranking.md` 참조.
- 구현: `domain/ranking/controller/RankingController` → `domain/ranking/service/RankingQueryService#getPopularMenus` → `RankingRedisKeys.recentRankingKeys(today)`로 만든 최근 7일 일자 키를 `StringRedisTemplate.opsForZSet().unionWithScores(...)`(읽기 전용 `ZUNION`)로 합산 → score 내림차순, 동점이면 menuId 오름차순으로 정렬해 상위 3개 → `MenuRepository#findAllById` 한 번으로 이름 조인(N+1 방지) → `PopularMenuResponse.from(rank, menu, orderCount)`.
- 랭킹 ZSET에는 있으나 `MenuRepository`에서 조회되지 않는 menuId(메뉴가 DB에서 사라진 경우)는 경고 로그만 남기고 스킵한다 — 랭킹 버킷(8일 TTL)과 메뉴 테이블이 독립적으로 진화할 수 있어서다.
- **(#47) `RankingController`가 `MenuController`와 같은 `/api/menus` 경로를 공유하는 이유**: URL 경로는 "메뉴라는 리소스"를 기준으로 묶었고(`/api/menus`, `/api/menus/popular`), 자바 패키지 경계는 "그 로직을 누가 소유·구현하는가"(집계/캐싱은 ranking 도메인) 기준이라 서로 다른 축이다. `/api/menus/popular`는 인기 메뉴라는 메뉴 리소스의 하위 뷰이지 ranking 도메인의 소유물이 아니므로, 컨트롤러를 옮기지 않고 현재 위치를 유지한다.

## 규칙 / 검증
- **(#39) 7일 union 정렬은 Redis가 아니라 애플리케이션에서 한다**: `unionWithScores`로 7일치 ZSET 멤버 전체를 가져와 자바에서 정렬 후 상위 3개만 사용한다. `ZUNIONSTORE`+`ZREVRANGE`로 Redis 쪽에서 정렬까지 끝내는 방법도 있지만, 이 프로젝트는 메뉴 생성 API가 없는 seed-only 소규모 카탈로그(#8 self-review에서 이미 같은 이유로 검토·기각된 결정과 동일선상)라 지금 복잡도를 더할 이유가 없다고 판단해 채택하지 않았다. **재검토 조건**: 메뉴가 수천 개 규모가 되어 union 결과 크기가 실제로 조회 성능에 영향을 줄 때.
- **(#38) 조회 경로엔 DB 트랜잭션을 걸지 않는다**: 이 메서드는 대부분 Redis I/O(`unionWithScores`)이고 DB 접근은 `menuRepository.findAllById` 한 번뿐이다. 클래스 레벨 `@Transactional(readOnly = true)`가 있으면 Redis 응답을 기다리는 동안 DB 커넥션을 붙잡아, 트래픽이 몰릴 때 커넥션 풀이 Redis 지연 때문에 먼저 고갈될 수 있었다(자체 리뷰에서 발견) — 제거했다. `menuRepository.findAllById`는 Spring Data JPA가 트랜잭션이 없으면 자기 자신만의 짧은 트랜잭션을 열어 처리하므로 별도 전파 설정 없이 안전하다.
- 정렬: score 내림차순 → 동점이면 menuId 오름차순(결정적 순서). 근거: `docs/policy/popular-menu.md`.
- 검증 레벨: Level 1(단위)·Level 3~4(Redis+DB 통합, `RankingQueryServiceTest`·`RankingQueryServiceUnitTest`)·Level 6(실제 HTTP) PASS. 상세 근거는 `docs/logs/ranking/popular/001-popular.md`.

## 관련 문서
- `docs/api/ranking.md` · `docs/policy/popular-menu.md` · [ADR-003](../../../adr/ADR-003-인기메뉴-집계전략.md)
- `docs/dev/ranking/consume/design.md` (집계/쓰기 쪽, `RANKING_ZONE`·`recentRankingKeys` 등 공유 키 포맷)
