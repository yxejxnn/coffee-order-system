# 다중 인스턴스 통합 검증

대상: verify/multi-instance
이슈: [#9](https://github.com/yxejxnn/coffee-order-system/issues/9)

## 배경 / 요구
발제 도전 요구 종합 — 앱이 다수 인스턴스로 떠도 기능·정합성 유지 확인.

## 설계 (HOW)
- **컨테이너화 대신 로컬 JVM 프로세스 2개**(`SERVER_PORT` 다름)로 다중 인스턴스를 구성한다. 기존 `docker compose up -d`(mysql/redis/kafka)를 그대로 재사용하고, Dockerfile은 1회성 검증을 위해 새로 만들지 않는다(유지비용 대비 효용 낮음).
- **`src/test/java/com/coffeeorder/verify/MultiInstanceVerificationTest.java`**: 이미 떠 있는 두 인스턴스에 대한 외부 관찰자로 동작하는 JUnit 테스트(`@Tag("multi-instance")`, 기본 `./gradlew test`에서 제외).
  - HTTP: `java.net.http.HttpClient`로 기존 `POST /api/points/charge`, `POST /api/orders`를 두 인스턴스 포트(기본 8080/8081, 시스템 프로퍼티로 override)에 나눠 호출.
  - DB 확인: 순수 JDBC(`DriverManager`, `mysql-connector-j` 이미 런타임 의존성)로 `POINT.balance`/`ORDERS` 카운트를 폭풍 전/후 **델타**로 비교(시드 회원의 잔여 잔액과 무관하게 정확).
  - 랭킹 확인: `/api/menus/popular`(top3) 대신 Redis `ZSCORE`(`menu:ranking:{yyyy-MM-dd}`, `RankingRedisKeys` 키 포맷 재사용)를 Lettuce로 직접 델타 비교(top3 밖으로 밀리는 오탐 방지). Kafka 컨슈머 랙 고려해 최대 10초 폴링.
  - 시나리오 1(충전): 시드 회원 하나에 소액 충전 N건을 절반씩 두 인스턴스로 동시 발사 → 잔액 델타 == N×충전액(lost update 없음).
  - 시나리오 2(주문): 다른 시드 회원에 감당 가능한 만큼만 충전 후 그보다 많은 동시 주문을 절반씩 두 인스턴스로 발사 → 잔액 델타 0(초과차감 없음), 주문수 델타 == 감당가능수, 성공/실패(409) 개수 어서션, 랭킹 ZSCORE 델타 == 감당가능수.
- **컨슈머 그룹 관찰**: 토픽 파티션 수를 명시 지정하지 않아(브로커 기본값) 사실상 1파티션 — `ranking-group`/`collector-group` 모두 한 시점엔 인스턴스 중 하나만 활성 컨슈머(정상 동작, 버그 아님). design.md에 사실로만 기록, 파티션 확장 등 별도 조치 없음.
- **`build.gradle`**: 기본 `test` 태스크는 `excludeTags 'multi-instance'`로 위 테스트 제외(2프로세스 전제 조건이 있어 일반 빌드를 깨뜨리면 안 됨). 신규 `verifyMultiInstance` Test 태스크(`includeTags 'multi-instance'`)로만 실행.
- **`scripts/verify-multi-instance.sh`**: `.env` 로드 → 인프라 기동 대기 → `bootJar` → 인스턴스 2개 백그라운드 기동(`SERVER_PORT` 다름) → `GET /api/menus` 헬스체크 대기 → `verifyMultiInstance` 실행 → `trap`으로 프로세스 정리. 재현 가능한 검증 오케스트레이션.

## 관련 결정·질문
- [`ADR-001`](../../../../adr/ADR-001-포인트-동시성제어.md) · [`ADR-002`](../../../../adr/ADR-002-주문이벤트-비동기전달.md) · [`ADR-003`](../../../../adr/ADR-003-인기메뉴-집계전략.md)
- 컨테이너화(Dockerfile) 대신 로컬 JVM 2프로세스로 결정 — 이슈 로드맵의 "compose(인스턴스 2개)" 표현과 다르지만, "다중 인스턴스"의 본질(별도 프로세스 2개가 공유 인프라에 접근)은 동일하게 충족.

## 태스크
- [x] `MultiInstanceVerificationTest` 작성(충전/주문 시나리오)
- [x] `build.gradle` 태그 분리 + `verifyMultiInstance` 태스크
- [x] `scripts/verify-multi-instance.sh` 작성
- [x] 실제 실행으로 Level 5·6 검증(PASS)
- [x] `docs/dev/verify/multi-instance/design.md` + `docs/logs/verify/multi-instance/001-verify.md` 기록

## 평가(통과) 기준
- 2인스턴스 동시 요청에서 초과 차감 0·카운트 정확 — **Level 5~6**, 검증 로그 `docs/logs/`.
