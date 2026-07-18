# verify/multi-instance — Design

## 개요
앱을 별도 JVM 프로세스 2개(`SERVER_PORT` 다름)로 띄우고 같은 `docker compose` 인프라(MySQL/Redis/Kafka)를 공유시킨 뒤, 실제 HTTP로 동시 충전/주문 요청을 두 인스턴스에 나눠 쏴서 잔액·주문수·랭킹 카운트 정합성을 외부에서 관찰·증명한다. 기존 `*ConcurrencyTest`들이 같은 JVM 안에서 서비스 메서드를 직접 동시 호출하는 것과 달리, 이 검증은 서로 다른 프로세스 두 개가 실제로 존재한다는 발제 도전 요구(다수 서버·다수 인스턴스)를 직접 증명한다.

## API / 인터페이스
- 새 엔드포인트 없음 — 기존 `POST /api/points/charge`(#4), `POST /api/orders`(#5)를 실제 HTTP로 두 인스턴스 포트에 나눠 호출한다.
- 검증 도구: `src/test/java/com/coffeeorder/verify/MultiInstanceVerificationTest.java`(`@Tag("multi-instance")`, 기본 `./gradlew test`에서 제외, `verifyMultiInstance` 태스크로만 실행). 두 인스턴스가 이미 떠 있다는 전제로 동작하는 외부 관찰자 테스트다.
- 오케스트레이션: `scripts/verify-multi-instance.sh` — 인프라 기동 대기 → `bootJar` → 인스턴스 2개 순차 기동(시딩 경쟁 완화) → 헬스체크(시딩 완료까지 확인) → `verifyMultiInstance` 실행 → 프로세스 정리(`trap`).

## 데이터 모델
- 별도 테이블 없음. 기존 `points.balance`/`orders`/Redis `menu:ranking:{yyyy-MM-dd}` ZSET(`RankingRedisKeys`)을 그대로 관찰 대상으로 삼는다.
- 검증 테스트는 `mysql-connector-j`(순수 JDBC)와 Lettuce(`spring-boot-starter-data-redis` 전이 의존성)로 앱과 별도 프로세스에서 직접 DB/Redis를 읽는다 — 서로 다른 JVM이라 리포지토리를 공유할 수 없어, 기존 단일-JVM 테스트가 리포지토리로 확인하던 것의 크로스-프로세스 대응물이다.

## 규칙 / 검증
- **컨테이너화 대신 로컬 JVM 2프로세스**를 채택했다. "다중 인스턴스"의 본질은 별도 프로세스 2개가 공유 인프라에 접근하는 것이지 컨테이너화 자체가 아니며, 1회성 검증을 위해 Dockerfile을 새로 만들고 유지하는 비용은 정당화되지 않는다고 판단했다.
- **잔액/주문수는 폭풍 전/후 델타로 비교**한다. 시드 회원의 잔여 잔액(반복 실행 누적분 포함)과 무관하게 정확하도록 하기 위함이다. 주문 시나리오의 "정확히 소진" 판정은 델타가 `0`이 아니라 `-(menuPrice * 감당가능수)`여야 한다는 점에 주의한다 — `balanceBefore`를 사전 충전 **이후** 시점에 읽기 때문에, 소진 후에는 그만큼 줄어드는 것이 정답이다.
- **랭킹 카운트는 `/api/menus/popular`(top3) 대신 Redis `ZSCORE` 델타로 직접 검증**한다. top3 API는 기존에 누적된 다른 메뉴 데이터가 많으면 검증 대상 메뉴가 순위 밖으로 밀려 오탐할 수 있어 배제했다. `ZSCORE` 델타 비교는 기존 데이터 유무와 무관하게 정확하다. Kafka 컨슈머 랙을 고려해 최대 10초 폴링한다.
- **컨슈머 그룹 관찰(버그 아님, 사실 기록)**: `order-completed` 토픽에 파티션 수를 명시 지정하지 않아(브로커 기본값, 실질 1파티션) `ranking-group`/`collector-group` 모두 한 시점엔 인스턴스 중 하나만 활성 컨슈머가 된다. "두 인스턴스 중 어디서 집계되든 카운트가 정확"이라는 이 이슈의 시나리오는 이 사실 위에서 성립하며(한쪽이 소비, 다운되면 리밸런싱), 로드밸런싱 분산 자체는 이 이슈의 완료조건이 아니라 별도 조치를 하지 않았다.
- **DataSeeder 시드 경쟁의 잔여 위험**: `DataSeeder`는 `count() == 0`일 때만 시드하는 존재 검증 없는 조건이라, 두 인스턴스가 완전히 동시에 기동하면 이론상 둘 다 빈 테이블을 보고 중복 시드를 시도할 수 있다. `scripts/verify-multi-instance.sh`는 인스턴스1이 시딩까지 마친 뒤(헬스체크가 메뉴 데이터 존재까지 확인) 인스턴스2를 띄워 이 경쟁을 완화하지만 완전히 배제하지는 않는다 — DataSeeder 자체 수정(예: 존재 검증 강화)은 이 이슈(#9)의 범위를 벗어나 손대지 않았다.
- 검증 레벨: **Level 5**(로컬 2인스턴스 실기동) · **Level 6**(실제 HTTP, 두 시나리오 모두) PASS. 상세 근거는 `docs/logs/verify/multi-instance/001-verify.md`.
- 기본 `./gradlew test`에는 `MultiInstanceVerificationTest`를 포함하지 않는다(2프로세스가 실제로 떠 있어야만 통과하는 전제가 있어 일반 빌드/CI를 깨뜨리면 안 됨) — `build.gradle`의 `excludeTags 'multi-instance'` + 전용 `verifyMultiInstance` 태스크로 분리했다.
- **`excludeTags`는 Gradle `test` 태스크에만 적용되는 설정**이라, IntelliJ 등 IDE 네이티브 JUnit 러너로 돌리면 이 제외를 모르고 그냥 실행한다. 이 경우 사전조건(2인스턴스 기동) 미충족을 예외(`FAILED`)가 아니라 `Assumptions.assumeTrue`(`ABORTED`)로 알리도록 `waitUntilReady()`를 작성했다 — 이건 JUnit Platform 표준 동작이라 러너와 무관하게 "실패"가 아니라 "스킵됨"으로 정확히 표시된다.
- **`DB_HOST`/`DB_PORT`/`DB_NAME`은 기본값을 두지 않는다**(`DB_USERNAME`/`DB_PASSWORD`와 동일하게 fail-fast). 이 개발 환경엔 자격증명이 서로 다른 MySQL이 두 개 공존해서(도커 3307, 로컬 3306 — IntelliJ의 JUnit 기본 실행 설정은 3306용 자격증명이 이미 박혀 있음) 어떤 기본값을 골라도 누군가의 환경에선 "조용히 엉뚱한 DB에 접속 시도"가 된다. 실행자가 항상 명시하게 강제해 혼란스러운 `Access denied` 대신 즉시(1초) 무엇을 설정해야 하는지 알려주는 실패로 대체했다.

## 관련 문서
- [`ADR-001`](../../../adr/ADR-001-포인트-동시성제어.md) · [`ADR-002`](../../../adr/ADR-002-주문이벤트-비동기전달.md) · [`ADR-003`](../../../adr/ADR-003-인기메뉴-집계전략.md) — 이 검증이 종합적으로 증명하는 대상.
- `docs/dev/point/charge/design.md` · `docs/dev/order/create/design.md` · `docs/dev/ranking/consume/design.md` — 검증 대상 기능들의 SSOT.
