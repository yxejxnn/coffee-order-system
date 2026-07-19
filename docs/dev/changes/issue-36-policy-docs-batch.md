# 정책 결정 문서화 일괄

대상: order/ranking(ADR-003), ranking/query, infra(db), ranking/popular 라우팅, point 정책
이슈: [#36](https://github.com/yxejxnn/coffee-order-system/issues/36), [#39](https://github.com/yxejxnn/coffee-order-system/issues/39), [#45](https://github.com/yxejxnn/coffee-order-system/issues/45), [#47](https://github.com/yxejxnn/coffee-order-system/issues/47), [#48](https://github.com/yxejxnn/coffee-order-system/issues/48) (같은 PR로 묶어 처리)
담당: Claude

## 배경 / 요구
`/code-review ultra` 클린업 라운드에서 나온 항목 중, 구현이 아니라 "이미 내린 설계 결정을 문서로 뒷받침하거나 트레이드오프를 명시"하는 성격의 5개를 하나의 문서 전용 PR로 묶었다. 코드 동작 변경 없음(`application.yml` 주석 1건 제외).

- #36: ADR-003이 "Redis 유실 시 ORDERS로 재구축"이라 주장하지만 `OrderRepository`에 재구축 쿼리가 없고 `idx_created_menu`가 죽은 인덱스.
- #39: 랭킹 union 정렬을 Redis(`ZUNIONSTORE`)가 아니라 애플리케이션에서 처리 — 카탈로그가 커지면 조회 성능 우려.
- #45: `ddl-auto: update`에만 의존, Flyway 등 마이그레이션 도구 없이 스키마 문서(`docs/db/`)와 반영 메커니즘이 분리.
- #47: `/api/menus/popular`가 menu 도메인이 아닌 ranking 패키지의 컨트롤러에 있어 라우팅을 찾기 어려움.
- #48: `INSUFFICIENT_POINT`가 409(Conflict)인데 422(Unprocessable Entity)가 더 일반적이라는 지적.

## 결정
- **#36**: 재구축 쿼리 미구현 유지. ADR-003/policy 문구를 "재구축 가능한 구조(인덱스)만 마련, 쿼리는 필요 시점에 구현"으로 하향.
- **#39**: 현행 애플리케이션 정렬 유지. seed-only 소규모 카탈로그에서 `ZUNIONSTORE` 도입은 과한 복잡도라는 근거를 design.md에 명시, 재검토 조건(메뉴 수천 개 규모) 기록.
- **#45**: Flyway 도입 안 함. 단일 환경 과제라는 근거를 `application.yml` 주석 + `docs/db/README.md`에 명시.
- **#47**: 컨트롤러 위치 유지. "경로=리소스 기준, 패키지=로직 소유 기준"이라는 서로 다른 축이라는 근거를 design.md·api 문서에 명시.
- **#48**: 409 유지. "형식 오류=400 / 상태 충돌=409" 일관 정책으로 `IDEMPOTENCY_KEY_CONFLICT`와 같은 카테고리라는 근거를 policy/point.md에 명시.

## 수정 파일
- `docs/adr/ADR-003-인기메뉴-집계전략.md`, `docs/policy/popular-menu.md` (#36)
- `docs/dev/ranking/popular/design.md` (#39, #47)
- `src/main/resources/application.yml`, `docs/db/README.md` (#45)
- `docs/api/ranking.md` (#47)
- `docs/policy/point.md` (#48)

## 태스크
- [x] #36 ADR-003 · policy 문구 하향
- [x] #39 design.md 트레이드오프 명시
- [x] #45 application.yml 주석 + docs/db/README.md 절 추가
- [x] #47 design.md · api/ranking.md 라우팅 근거 추가
- [x] #48 policy/point.md 409 근거 추가

## 평가(통과) 기준
- 문서 전용 변경 — 기존 테스트 회귀 없음(코드 로직 변경 없음, `./gradlew compileJava`로 yml 주석 무해함만 확인).
