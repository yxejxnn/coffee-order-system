# ranking/popular — Design

## 개요
최근 7일 인기 메뉴 상위 3개를 조회한다(발제 4번). 집계(쓰기, #7 `ranking/consume`)와 조회(읽기)를 분리해, 조회는 Redis ZSET을 읽기 전용으로만 사용한다.

## API / 인터페이스
- `GET /api/menus/popular` — 상세 계약은 `docs/api/ranking.md` 참조.
- 구현: `domain/ranking/controller/RankingController` → `domain/ranking/service/RankingQueryService#getPopularMenus` → `RankingRedisKeys.recentRankingKeys(today)`로 만든 최근 7일 일자 키를 `StringRedisTemplate.opsForZSet().unionWithScores(...)`(읽기 전용 `ZUNION`)로 합산 → score 내림차순, 동점이면 menuId 오름차순으로 정렬해 상위 3개 → `MenuRepository#findAllById` 한 번으로 이름 조인(N+1 방지) → `PopularMenuResponse.from(rank, menu, orderCount)`.
- 랭킹 ZSET에는 있으나 `MenuRepository`에서 조회되지 않는 menuId(메뉴가 DB에서 사라진 경우)는 경고 로그만 남기고 스킵한다 — 랭킹 버킷(8일 TTL)과 메뉴 테이블이 독립적으로 진화할 수 있어서다.

## 규칙 / 검증
- **(#38) 조회 경로엔 DB 트랜잭션을 걸지 않는다**: 이 메서드는 대부분 Redis I/O(`unionWithScores`)이고 DB 접근은 `menuRepository.findAllById` 한 번뿐이다. 클래스 레벨 `@Transactional(readOnly = true)`가 있으면 Redis 응답을 기다리는 동안 DB 커넥션을 붙잡아, 트래픽이 몰릴 때 커넥션 풀이 Redis 지연 때문에 먼저 고갈될 수 있었다(자체 리뷰에서 발견) — 제거했다. `menuRepository.findAllById`는 Spring Data JPA가 트랜잭션이 없으면 자기 자신만의 짧은 트랜잭션을 열어 처리하므로 별도 전파 설정 없이 안전하다.
- 정렬: score 내림차순 → 동점이면 menuId 오름차순(결정적 순서). 근거: `docs/policy/popular-menu.md`.
- 검증 레벨: Level 1(단위)·Level 3~4(Redis+DB 통합, `RankingQueryServiceTest`·`RankingQueryServiceUnitTest`)·Level 6(실제 HTTP) PASS. 상세 근거는 `docs/logs/ranking/popular/001-popular.md`.

## 관련 문서
- `docs/api/ranking.md` · `docs/policy/popular-menu.md` · [ADR-003](../../../adr/ADR-003-인기메뉴-집계전략.md)
- `docs/dev/ranking/consume/design.md` (집계/쓰기 쪽, `RANKING_ZONE`·`recentRankingKeys` 등 공유 키 포맷)
