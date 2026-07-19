# 002-transaction-scope — 불필요한 DB 트랜잭션 제거 (로그)

## Attempt 1 — 2026-07-19  ✅ PASS
- 시도: `RankingQueryService`의 클래스 레벨 `@Transactional(readOnly = true)` 제거(#38). 대부분 Redis I/O인 메서드 전체를 DB 트랜잭션으로 감싸 커넥션을 불필요하게 오래 붙잡던 문제. `menuRepository.findAllById`는 Spring Data JPA 자체 트랜잭션으로 충분해 안전.
- 결과: `./gradlew test --tests "*Ranking*"` PASS, 회귀 없음(`RankingQueryServiceTest`는 실제 Redis+DB로 수동 cleanup을 쓰지 트랜잭션 롤백에 의존하지 않음을 코드로 확인 후 진행).
- 검증 레벨: Level 1(단위+회귀) PASS.
- 비고: 이 기능(`ranking/popular`, #8)에 `design.md`가 원래 없어(누락) 이번에 새로 작성해 채움(`docs/dev/ranking/popular/design.md`).
