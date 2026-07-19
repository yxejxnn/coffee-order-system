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

## Attempt 5 — 2026-07-19  ✅ PASS (자체 리뷰 반영, 설계 변경)
- 시도: PR #51 자체 리뷰(`/code-review --comment`, high effort, 8각도 서브에이전트 4개 병렬). 가장 심각한 발견:
  1. **CONFIRMED(치명적, 2개 앵글 독립 확인)** — Attempt 4의 `isolation = Isolation.READ_COMMITTED`가 `order/create(#5)`를 통한 실제 주문결제 경로에서 **무효화**됨. `OrderService.create()`가 이미 자신의 `@Transactional`(propagation 기본값 `REQUIRED`)을 연 상태에서 `pointService.use()`를 호출하는데, `use()`도 propagation 미지정(`REQUIRED`)이라 새 트랜잭션을 시작하지 않고 이미 열린 바깥 트랜잭션에 참여(join)한다. Spring은 참여 시 `isolation` 속성을 적용하지 않고 `validateExistingTransaction`(기본 `false`)이라 예외도 없이 조용히 무시한다 — 즉 정작 가장 중요한 프로덕션 경로에는 이 fix가 전혀 적용되지 않고 있었다. `PointServiceConcurrencyTest`(직접 호출)는 이 경로를 검증 못 하고, `OrderServiceConcurrencyTest`는 Point를 항상 미리 만들어둬서 이 시나리오 자체를 트리거하지 않아 이 구멍을 가리고 있었다.
  2. **CONFIRMED** — `catch (DataIntegrityViolationException e)`만으로는 부족할 수 있음: 커밋 타이밍이 겹쳐 락 대기가 `innodb_lock_wait_timeout`을 넘기면 `CannotAcquireLockException`으로 나타나 이 catch를 빠져나갈 수 있음.
  3. **PLAUSIBLE** — `PointBootstrapService`에 클래스 레벨 `@Transactional(readOnly = true)` 기본값 누락(`docs/code-convention.md` 컨벤션 위반).
  4. **PLAUSIBLE** — HikariCP `maximum-pool-size: 20`이 이론적 최악 케이스(10스레드×2커넥션)와 정확히 같아 여유 없음.
  5. **PLAUSIBLE**(테스트 품질) — `java.util.Collections`/`ArrayList`를 FQN으로 인라인 사용, import 블록과 불일치.
  6. **트레이드오프로 기록(반영 안 함)** — REQUIRES_NEW 분리로 인한 원자성 완화(생성만 먼저 커밋될 수 있음), `DataIntegrityViolationException`을 제약 종류 구분 없이 넓게 catch.
  - **수정(1번, 설계 변경)**: 격리수준에 의존하지 않는 방식으로 재설계. `PointRepository`에 잠금 없는 `existsByMemberId` 추가. `getLockedPoint`가 이걸로 먼저 존재를 확인하고, 없을 때만 `ensurePointCreated`(구 `createPointForExistingMember`)로 생성한 뒤에 비로소 `SELECT ... FOR UPDATE`를 건다 — 이러면 그 시점엔 행이 항상 이미 존재해 갭 락 자체가 발생할 수 없다(호출자의 트랜잭션 전파·격리수준과 무관하게 안전). `charge`/`use`의 `isolation = READ_COMMITTED`는 더 이상 필요 없어 제거.
  - **수정(2번)**: catch를 `DataIntegrityViolationException | CannotAcquireLockException`으로 확장.
  - **수정(3번)**: `PointBootstrapService`에 `@Transactional(readOnly = true)` 클래스 레벨 추가.
  - **수정(4번)**: `maximum-pool-size`를 25로 상향(최소 20 + 여유 5).
  - **수정(5번)**: FQN 제거, `import java.util.ArrayList/Collections` 추가.
  - **신규 회귀 테스트**: `OrderServiceConcurrencyTest`에 "Point 없는 회원으로 동시 첫 주문"(`create_concurrentFirstOrdersOnMemberWithoutPoint_noDeadlock`) 케이스 추가 — `OrderService.create()`가 이미 연 트랜잭션 안에서 호출되는 실제 경로로 1번 발견사항을 재현·검증.
  - PR 인라인 코멘트에 요약 반영, 각 스레드 resolve 예정.
- 결과: `./gradlew test --tests "*PointService*" --tests "*OrderServiceConcurrencyTest*"` 연속 3회 PASS. 전체 `./gradlew test` 61건(신규 1건 포함) 전부 PASS, 회귀 없음.
- 검증 레벨: Level 1(단위+전체 회귀) PASS · Level 3(락·동시성 통합) PASS — `PointServiceConcurrencyTest`(직접 호출 경로) + `OrderServiceConcurrencyTest`(주문결제를 통한 실제 호출 경로, Point 없는 회원 15스레드 동시 주문) 둘 다 데드락 없이 통과.
