# 커피 주문 시스템

포인트로 결제하는 커피 주문 백엔드. **다수 인스턴스로 떠 있어도 잔액과 주문 횟수가 어긋나지 않는 것**을 1순위 목표로 설계했다.

- 스택: Java 17 · Spring Boot 4.1 · JPA · MySQL 8 · Redis 7 · Kafka 3.8(KRaft) · Gradle
- 설계 근거는 [ADR](docs/adr/)에, API·스키마·정책 명세는 [docs/api](docs/api/) · [docs/db](docs/db/) · [docs/policy](docs/policy/)에 있다.

## 요구사항 대응

| # | 요구사항 | 구현 | 명세 |
|---|---------|------|------|
| 1 | 커피 메뉴 목록 조회 | `GET /api/menus` | [api/menu](docs/api/menu.md) |
| 2 | 포인트 충전 | `POST /api/points/charge` | [api/point](docs/api/point.md) |
| 3 | 커피 주문·결제 + 주문내역 데이터 수집 플랫폼 전송 | `POST /api/orders` → Kafka → 수집 플랫폼(Mock) 전송 | [api/order](docs/api/order.md) |
| 4 | 최근 7일 인기 메뉴 3개 | `GET /api/menus/popular` | [api/ranking](docs/api/ranking.md) |
| 도전 | 동시성·다수 인스턴스 정합성 | 비관적 락 + Kafka + Redis 멱등 집계, 2인스턴스 통합 검증 | [ADR-001](docs/adr/ADR-001-포인트-동시성제어.md) · [ADR-003](docs/adr/ADR-003-인기메뉴-집계전략.md) |

---

## 실행 방법

### 1) 인프라 기동

```bash
cp .env.example .env          # docker compose가 읽는 값 (MySQL 비밀번호 등)
docker compose up -d          # mysql(3307) · redis(6379) · kafka(9092)
```

> MySQL 컨테이너는 호스트 **3307**에 노출한다(로컬에 이미 3306 MySQL이 떠 있는 경우를 피하기 위함).

### 2) 애플리케이션 실행

`.env`는 docker compose 전용이라 **Spring 앱은 읽지 않는다.** DB 자격증명은 환경변수로 직접 넘긴다(기본값 없이 fail-fast — `application.yml`이 공개 파일이라 크리덴셜에는 기본값을 두지 않았다).

```bash
export DB_HOST=localhost DB_PORT=3307 DB_NAME=coffee_order
export DB_USERNAME=root DB_PASSWORD=<.env의 MYSQL_ROOT_PASSWORD>

./gradlew bootRun
```

기동 시 `DataSeeder`가 회원 3명(+포인트 0원)과 메뉴 5개를 시드한다. 회원가입·메뉴등록 API는 발제 범위 밖이라 만들지 않았다.

### 3) 동작 확인

```bash
curl localhost:8080/api/menus
curl -X POST localhost:8080/api/points/charge \
  -H 'Content-Type: application/json' -d '{"memberId":1,"amount":20000}'
curl -X POST localhost:8080/api/orders \
  -H 'Content-Type: application/json' -d '{"memberId":1,"menuId":1,"quantity":2}'
curl localhost:8080/api/menus/popular
```

### 4) 테스트

```bash
./gradlew test                        # 단위 + 통합 (인프라 기동 상태 필요)
sh scripts/verify-multi-instance.sh   # 2인스턴스 동시성 검증 (아래 "검증" 참고)
```

---

## 기술 선택 이유

| 기술 | 왜 |
|------|-----|
| **MySQL 비관적 락** | 모든 인스턴스가 **하나의 MySQL을 공유**하므로 "멀티 인스턴스 정합성" 문제가 "한 행에 대한 동시 접근" 문제로 환원된다. DB 락으로 이미 풀리는 문제에 분산 락(Redisson)을 얹는 건 중복이라고 판단했다. → [ADR-001](docs/adr/ADR-001-포인트-동시성제어.md) |
| **Kafka** | 주문 완료 후 할 일이 둘(수집 플랫폼 전송 · 인기 메뉴 집계)이고 앞으로 더 늘 수 있다. 이걸 결제 트랜잭션 안에서 동기로 하면 외부 지연이 결제 실패로 전파된다. 컨슈머 그룹 추가만으로 후속 처리를 늘릴 수 있는 구조를 택했다. → [ADR-002](docs/adr/ADR-002-주문이벤트-비동기전달.md) |
| **Redis ZSET** | 인기 메뉴는 사용자 대면 조회라 빨라야 하고, `ZINCRBY`가 원자적이라 여러 인스턴스가 동시에 올려도 합산이 깨지지 않는다. 앱 메모리 집계는 인스턴스별로 값이 갈려서 애초에 후보가 아니었다. → [ADR-003](docs/adr/ADR-003-인기메뉴-집계전략.md) |
| **Kafka 단일 브로커** | 발제의 "다중 인스턴스"는 **앱 프로세스**를 여러 개 띄우는 요구이고, 브로커 다중화는 그와 별개인 Kafka 자체의 내구성 주제다. 과제 범위에서 3-브로커 클러스터는 검증 비용만 늘린다고 보고 단일 브로커(KRaft)로 두었다. |

---

## 설계 내용

### 데이터 모델

전체 ERD와 테이블별 상세는 [docs/db/erd.md](docs/db/erd.md).

```
MEMBER ─1:1─ POINT              (잔액)
   ├──1:N─ POINT_HISTORY        (충전/사용 이력, append-only)
   └──1:N─ ORDERS ─N:1─ MENU
```

설계 의도 3가지:

- **`POINT`를 `MEMBER`에서 1:1 분리** — 충전/차감 시 잠그는 대상이 **잔액 행 하나**가 되도록. 회원 row에 `balance` 컬럼을 두면 이름 같은 무관한 필드까지 함께 잠긴다. FK는 `POINT`가 `member_id`를 갖는 방향인데, 핵심 엔티티(`MEMBER`)가 부가 상태(잔액)를 몰라야 하고 조회·락 패턴이 항상 "회원 → 포인트" 방향이기 때문이다. → [ADR-004](docs/adr/ADR-004-데이터모델-포인트분리.md)
- **`ORDER_ITEM` 없이 `ORDERS` 1행 = 주문 1건** — 발제 API는 메뉴 1건 주문이고 주문 상세 조회도 없다. 대신 `order_group_id`(UUID)를 두어 **결제 식별자 · Kafka 이벤트 멱등 키**를 겸하게 했다. 나중에 다건 카트가 필요해지면 같은 `order_group_id`로 여러 행을 묶으면 된다.
- **엔티티 간 참조를 `@ManyToOne` 객체 대신 FK id(`Long`)로** — 도메인별 패키징(`domain.{member,point,menu,order,ranking,collector}`)을 했는데 연관관계 객체를 쓰면 엔티티 계층에서 도메인 간 컴파일 의존이 다시 생긴다. API 응답 스펙을 전부 확인한 결과 객체 그래프 탐색이 필요한 곳이 하나도 없었다. 대가로 DB FK 제약이 사라지므로 참조 무결성은 서비스 레이어 검증에 의존한다. → [ADR-005](docs/adr/ADR-005-엔티티간-FK-ID-참조.md)

### 흐름

```
POST /api/orders
  └─ [트랜잭션] 멱등키 확인 → 검증 → POINT 행 비관적 락 → 잔액 차감
                → ORDERS 저장 → POINT_HISTORY(USE) 기록
  └─ 커밋 후(AFTER_COMMIT) OrderCompletedEvent → Kafka topic: order-completed
       ├─ collector-group → 데이터 수집 플랫폼(Mock) POST 전송
       └─ ranking-group   → Redis ZSET menu:ranking:{yyyy-MM-dd} ZINCRBY (멱등)

GET /api/menus/popular
  └─ 최근 7개 일자 키 union → 상위 3개 → MENU 이름 조인
```

---

## 문제 해결 전략

### 1. 동시성 — 같은 회원의 따닥 주문에도 잔액이 정확해야 한다

`POINT` 행에 `SELECT ... FOR UPDATE`(`@Lock(PESSIMISTIC_WRITE)`)를 걸어 충전·차감을 직렬화한다.

- **낙관적 락을 제외한 이유**: 동일 회원 반복 요청은 현실적으로 흔한 패턴이라 충돌 빈도가 낮지 않다. 충돌마다 예외+재시도가 도는 구조는 재시도 스톰 위험이 있다.
- **분산 락을 제외한 이유**: 공유 MySQL이 한 대이므로 DB 락이 이미 모든 인스턴스를 직렬화한다. Redis 락은 여기에 락 누수·TTL 관리라는 운영 위험만 더한다. DB가 샤딩되면 그때 재검토한다.
- **대가**: 같은 회원의 요청은 순차 처리되어 처리량이 제한된다. 다만 락 범위가 회원 1행이라 전체 병목은 아니다. 데드락 방지를 위해 락 획득 순서를 항상 `POINT` 먼저로 고정했다.

주문 재시도로 인한 이중 결제는 선택적 `Idempotency-Key` 헤더로 막는다 — 같은 키로 다시 오면 결제하지 않고 첫 응답을 그대로 돌려준다. 다른 회원이 쓴 키를 재사용하면 `409 IDEMPOTENCY_KEY_CONFLICT`.

### 2. 실시간 전송 — 결제 경로에 외부 의존을 넣지 않는다

`@TransactionalEventListener(AFTER_COMMIT)`으로 **트랜잭션 커밋 후** 이벤트를 발행한다. 커밋되지 않은 주문이 전송·집계되는 일(유령 데이터)이 원천적으로 없다.

- **동기 REST 직접 호출을 제외한 이유**: 수집 플랫폼이 느려지거나 죽으면 그게 그대로 결제 응답 지연·실패가 된다. 부가 경로가 핵심 경로를 인질로 잡는 구조다.
- **대가**: 커밋과 발행이 원자적이지 않아 드물게 이벤트 유실 가능성이 있다. 발행 재시도로 완화했고, 유실 0이 필요해지면 Outbox로 승격한다(아래 "제외한 것" 참고). → [ADR-002](docs/adr/ADR-002-주문이벤트-비동기전달.md)
- 수집 플랫폼은 실제로 존재하지 않으므로 같은 앱 안의 `MockCollectorController`(`POST /mock/collector/orders`)로 대체하고 `memberId · menuId · 결제금액`을 보낸다. 전송 실패는 최대 3회 재시도(200ms 백오프) 후 ERROR 로그 — 수집은 유실이 허용되는 부가 경로로 판단했다.

### 3. 인기 메뉴 — "정확한 주문 횟수"를 at-least-once 위에서 만든다

Kafka는 at-least-once라 **같은 이벤트가 두 번 올 수 있다.** 발제가 "정확한 횟수"를 요구하므로 이걸 소비자 쪽에서 막아야 한다.

- 증가 전에 `order_group_id`를 Redis에 `SETNX`로 표식하고, 이미 있으면 skip → 재전송 중복 집계 차단.
- 실패 시 표식을 롤백한다. 처음 구현은 표식을 **먼저** 찍고 `ZINCRBY`를 했는데, 그 사이에 실패하면 재전송이 "이미 처리됨"으로 보고 그 주문을 **영원히 누락**시킨다 — 막으려던 중복보다 나쁜 결과여서 자체 리뷰에서 잡아 고쳤다.
- **일자별 버킷**(`menu:ranking:{yyyy-MM-dd}`, TTL 8일)이라 "최근 7일"이 키 7개 union으로 자연히 나오고 만료도 TTL로 끝난다. 시간대는 `Asia/Seoul` 고정.
- **원천은 Redis가 아니라 `ORDERS`다.** Redis는 조회 fast path일 뿐이고, 유실 시 `ORDERS`로 재구축 가능하도록 `idx_created_menu` 인덱스를 마련해뒀다.
- 집계 실패는 유실시키지 않는다 — 정확성이 요구사항이므로 collector와 달리 재시도 3회 후 **DLT(`order-completed-dlt`)**에 보존한다. → [ADR-003](docs/adr/ADR-003-인기메뉴-집계전략.md)

### 4. 검증 — "테스트 통과"가 아니라 "실제로 2개 띄워서 확인"

`scripts/verify-multi-instance.sh`가 인프라 기동 → `bootJar` → **앱 2 프로세스(8080/8081)** 기동 → 검증 테스트 → 정리까지 한다. 테스트는 `@SpringBootTest`가 아니라 **외부 관찰자**로 동작한다 — HTTP로 두 인스턴스를 동시에 때리고, 정답은 JDBC/Redis로 직접 읽는다.

- 동시 충전: 잔액 증가분 == `N × amount` (lost update 0)
- 동시 주문: 잔액 감소분 == `가격 × 결제성공건수`, **초과 판매 0**, 주문 행 수 정확, Redis 카운트 정확

기본 `./gradlew test`에서는 제외된다(2인스턴스 전제). 사전조건 미충족 시 실패가 아니라 `Assumptions`로 skip 처리해 어느 러너에서든 "건너뜀"으로 보이게 했다.

---

## 의도적으로 제외한 것 (트레이드오프)

과제 범위와 "설명할 수 있는 것만 넣는다"는 기준으로 뺀 것들이다.

| 제외 | 이유 / 재도입 조건 |
|------|-------------------|
| **Outbox 패턴** | 이벤트 유실 0에는 Outbox가 정답이지만 릴레이 구현·운영 부담이 있다. at-least-once + 소비자 멱등으로 정확성을 확보하는 쪽을 택했다. 전송 유실이 비즈니스상 허용 불가로 승격되면 도입. |
| **Redis 재구축 집계 쿼리** | 재구축 **가능한 구조**(인덱스)만 남기고 쿼리는 구현하지 않았다. 검증되지 않은 복구 경로를 미리 코드로 두는 것보다 정직한 스코프라고 판단. |
| **Flyway 등 마이그레이션** | 환경 분리가 없는 단일 환경이라 `ddl-auto: update`. 스키마 SSOT는 [docs/db](docs/db/)다. 다중 환경으로 가면 교체가 필요하다. |
| **CI 파이프라인** | 로컬 `./gradlew test` + 다중 인스턴스 스크립트로 검증한다. |
| **주문 취소/환불, 메뉴 재고, 충전 한도** | 발제 범위 밖. 정책 공백은 추측하지 않고 [open-questions](docs/open-questions.md)에 남겼다. |

**알려진 잔여 위험** — 완전히 동시에 같은 `Idempotency-Key`로 두 요청이 들어오면 하나는 유니크 제약에 걸려 실패(500)한다. 트랜잭션이 통째로 롤백되므로 **이중 차감은 없고**, 클라이언트가 같은 키로 재시도하면 첫 성공 건을 받는다. 최소 스코프 선택으로 남겨둔 지점.

---

## 문서 맵

| 폴더 | 내용 |
|------|------|
| [docs/adr/](docs/adr/) | 아키텍처 의사결정 기록 — 대안·트레이드오프·재검토 조건 |
| [docs/db/](docs/db/) | ERD와 테이블별 스키마 명세 |
| [docs/api/](docs/api/) | API 계약(요청·응답·에러 코드) |
| [docs/policy/](docs/policy/) | 포인트·인기 메뉴 비즈니스 규칙 |
| [docs/dev/](docs/dev/) | 기능별 상세 설계(`design.md`)와 완료 기록 |
| [docs/logs/](docs/logs/) | 실행·검증 시도 기록(성공/실패 포함) |
| [docs/open-questions.md](docs/open-questions.md) | 미결정 사항과 잠정 결정 |
