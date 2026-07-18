# 001-verify — 다중 인스턴스 통합 검증 (로그)

## Attempt 1 — 2026-07-18  ❌ FAIL
- 시도: `MultiInstanceVerificationTest`(충전 동시성 + 주문 동시성/랭킹 시나리오) · `scripts/verify-multi-instance.sh` · `build.gradle`의 `verifyMultiInstance` 태스크 구현. `docker compose`(이미 기동 중이던 mysql/redis/kafka, 2일째 실행 중) 위에 `SERVER_PORT=8080`/`8081` 두 인스턴스를 스크립트로 순차 기동해 실행.
- 결과: 충전 시나리오 PASS, 주문 시나리오 FAIL — `AssertionFailedError: [정확히 소진되어야 한다(초과차감 없음)] expected: 0L but was: -22500L` (`MultiInstanceVerificationTest.java:171`).
- 원인: 서버 로직이 아니라 **테스트 어서션 계산 실수**. `successCount`(5)·`insufficientCount`(10) 어서션은 이미 통과한 상태였다(= 서버는 정확히 감당 가능한 5건만 성공시키고 나머지 10건은 409로 거절해 동시성 로직 자체는 정상 동작). 그런데 `balanceBefore`를 **사전 충전 이후** 시점에 읽어놓고도 소진 후 델타를 `0`으로 기대했다 — `balanceBefore`가 이미 충전분(22500)을 포함하므로, 정확히 소진됐다면 델타는 `0`이 아니라 `-22500`(`-menuPrice*AFFORDABLE_ORDERS`)이 정답이었다.
- 다음: 어서션을 `balanceAfter - balanceBefore == -(menuPrice * AFFORDABLE_ORDERS)`로 수정.

## Attempt 2 — 2026-07-18  ✅ PASS
- 시도: 위 어서션 수정(`MultiInstanceVerificationTest.java`) 후 `scripts/verify-multi-instance.sh` 재실행. 인프라·인스턴스 기동 절차는 Attempt 1과 동일.
- 결과: `verifyMultiInstance` 2개 시나리오 모두 `BUILD SUCCESSFUL`(0 failures). 이어서 `./gradlew test`(기본 스위트, 56개+)도 전체 통과 확인 — `MultiInstanceVerificationTest`는 태그 제외로 목록에 나타나지 않음(태그 분리가 의도대로 동작).
- 검증 레벨: **Level 5**(로컬 2인스턴스 실기동 — `docker compose` mysql(3307)/redis(6379)/kafka(9092) 공유 위에 `SERVER_PORT=8080`/`8081` 두 독립 JVM 프로세스를 순차 기동, 인스턴스1 시딩 완료 확인 후 인스턴스2 기동) PASS · **Level 6**(실제 HTTP, `POST /api/points/charge`·`POST /api/orders`를 두 포트에 실제로 분산 호출) PASS.
- 증거(시나리오 1 — 충전 동시성):
  - 시드 회원 1명에게 소액 충전 20건(포트 8080/8081에 10건씩 동시 발사) → 전 건 200 성공(20/20) → JDBC로 읽은 `points.balance` 델타 = `20 * 1000 = 20000`(정확히 일치, lost update 없음).
- 증거(시나리오 2 — 주문 동시성 + 랭킹):
  - 다른 시드 회원에게 아메리카노(4500원) 5개 분량(22500원) 사전 충전(`POST /api/points/charge` → 200).
  - 동시 주문 15건(포트 8080/8081에 나눠 발사) → 성공(201) 정확히 5건, 거절(409, `INSUFFICIENT_POINT`) 정확히 10건.
  - `points.balance` 델타 = `-22500`(정확히 감당 가능한 만큼만 소진, 초과차감 없음).
  - `orders` 카운트 델타 = `5`(성공한 주문 수와 정확히 일치).
  - Redis `menu:ranking:{today}` ZSCORE 델타 = `5.0`(두 인스턴스 각각의 `ranking-group` 컨슈머 중 한쪽이 소비했든 카운트는 정확 — 멱등 처리(#7)가 다중 인스턴스에서도 성립함을 확인).
- 실행 환경: `docker compose`(mysql 3307, redis 6379, kafka 9092), `DB_HOST=localhost DB_PORT=3307 DB_NAME=coffee_order DB_USERNAME=root`(비밀번호는 `.env`의 `MYSQL_ROOT_PASSWORD`), `REDIS_HOST=localhost REDIS_PORT=6379`. 스크립트: `bash scripts/verify-multi-instance.sh`.
- 관찰(버그 아님, 사실 기록): `order-completed` 토픽이 실질 1파티션이라 `ranking-group`/`collector-group` 모두 한 시점엔 인스턴스 중 하나만 활성 컨슈머였다 — 예상된 동작이며 카운트 정확성엔 영향 없음(`docs/dev/verify/multi-instance/design.md` 참고).
