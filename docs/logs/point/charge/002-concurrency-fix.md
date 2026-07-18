# 002-concurrency-fix — 포인트 최초 생성 경로 동시성 구멍 제거 (로그)

## Attempt 1 — 2026-07-19  ❌ FAIL
- 시도: `createPointForExistingMember`의 insert를 `saveAndFlush` + `catch(DataIntegrityViolationException)` + 같은 트랜잭션 안에서 재조회로 구현(계획서 원안). `PointServiceConcurrencyTest`에 Point 없는 회원에 동시 30건 첫 충전 케이스 추가.
- 결과: 최종 잔액 21000(기대 30000) — lost update. 예외를 수집하도록 테스트를 보강해 재실행하니 `CannotAcquireLockException: Deadlock found when trying to get lock`가 원인.
- 원인: MySQL 기본 격리수준(REPEATABLE READ)에서 `SELECT ... FOR UPDATE`가 존재하지 않는 행에 갭 락을 걸어, 동시에 여러 트랜잭션이 같은 갭에서 충돌해 데드락.
- 다음: 데드락 원인을 없애야 함.

## Attempt 2 — 2026-07-19  ❌ FAIL
- 시도: `charge`/`use`에 `@Transactional(isolation = Isolation.READ_COMMITTED)` 추가(갭 락 회피). insert 방식은 네이티브 `INSERT IGNORE`로 변경(예외 없이 흡수).
- 결과: 여전히 데드락(`Deadlock found when trying to get lock`), 이번엔 `SELECT ... FOR UPDATE` 문에서.
- 원인: MySQL은 `INSERT`(IGNORE 포함)가 duplicate-key와 충돌하면 그 행에 공유 락(S-lock)을 잡는다. 같은 트랜잭션에서 곧바로 `SELECT ... FOR UPDATE`(X-lock 요청)로 이어지면, 같은 행에서 이런 일을 겪은 여러 트랜잭션이 서로의 공유 락을 기다리며 데드락(고전적인 락 승격 데드락) — READ_COMMITTED로도 해소 안 됨.
- 다음: insert 시도와 그 뒤의 FOR UPDATE 재조회를 서로 다른 트랜잭션으로 분리해야 함.

## Attempt 3 — 2026-07-19  ❌ FAIL (설계 안착, 세부 버그)
- 시도: `PointBootstrapService.ensurePointExists`를 신설해 `@Transactional(propagation = REQUIRES_NEW)`로 insert를 완전히 별도 트랜잭션으로 분리. 그 안에서 `saveAndFlush` + `catch(DataIntegrityViolationException)`로 무시.
- 결과: `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only`.
- 원인: JPA 스펙상 flush 실패는 EntityManager의 트랜잭션을 즉시 rollback-only로 표시한다 — 그 예외를 메서드 안에서 잡아 정상 반환해도, Spring이 반환 직후 커밋을 시도하다 rollback-only임을 발견해 새로 `UnexpectedRollbackException`을 던진다.
- 다음: 예외를 트랜잭션 메서드 **안에서** 잡지 말고, 그대로 던져 Spring이 정상적으로 롤백하게 한 뒤 **호출자**가 그 결과 예외를 잡아야 함.

## Attempt 4 — 2026-07-19  ✅ PASS
- 시도: `PointBootstrapService.ensurePointExists`에서 try-catch 제거(예외를 그대로 던짐 → Spring이 정상 롤백 후 원래 `DataIntegrityViolationException` 전파). `PointService.createPointForExistingMember`가 `ensurePointExists` 호출을 try-catch로 감싸 그 예외를 잡아 무시. `REQUIRES_NEW`가 스레드당 커넥션 2개를 동시에 요구하므로 HikariCP `maximum-pool-size`를 10→20으로 상향(`application.yml`).
- 결과: `./gradlew test --tests "*PointService*"` 연속 3회 안정적으로 PASS. 전체 `./gradlew test`(Docker MySQL/Redis/Kafka 기동 상태) 60건 전부 PASS(회귀 없음).
- 검증 레벨: Level 1(단위+전체 회귀) PASS · **Level 3(락·동시성 통합, `PointServiceConcurrencyTest` 신규 케이스)** PASS — 30개 동시 첫 충전 요청, 예외 없이 전부 완료·최종 잔액 정확히 30000·`Point` 행 정확히 1개·`PointHistory` 정확히 30건.
- 비고: `.env`의 `DB_PORT=3307`(로컬 MySQL 포트 충돌 회피용)이 `./gradlew test` 직접 실행 시 자동 로드되지 않아, 최초 실행에서 무관한 `HibernateException: Unable to determine Dialect` 오류로 착각할 뻔함 — `set -a && source .env && set +a`로 환경변수를 명시 로드해야 함(기존 dev 노트, 재확인).
