# 001-charge — 포인트 충전 API (+동시성 락) (로그)

## Attempt 1 — 2026-07-16  ✅ PASS
- 시도: `docs/api/point.md`·`docs/policy/point.md`·ADR-001대로 `PointController`/`PointService`/`PointRepository#findByMemberIdForUpdate`(`@Lock(PESSIMISTIC_WRITE)`)/`Point#charge`/`PointChargeRequest`·`PointChargeResponse` 구현. 도중 `docs/dev/domain/entity/design.md`(#2)에 "Point는 #4 첫 충전에서 lazy 생성"이 이미 문서화돼 있던 걸 발견 — 처음엔 이걸 "버그"로 오인해 사용자에게 잘못 설명한 채 승인을 받았다가, design.md를 다시 확인하고 정정 질문을 다시 드림. 최종적으로 사용자가 "DataSeeder가 회원과 함께 Point도 시드"로 확정(동시 첫 충전 시 unique 제약 충돌 처리를 피하기 위함) → `DataSeeder` 수정 + `docs/dev/domain/entity/design.md`·`docs/dev/point/charge/design.md` 갱신.
- 결과: `./gradlew test`(전체, 실 MySQL 대상) 24건 전부 PASS(신규: `PointServiceTest` 4·`PointControllerTest` 1·`PointServiceConcurrencyTest` 1·`DataSeederTest` 갱신 2).
- 검증 레벨: Level 1(단위+회귀 전체) PASS · Level 2(컨트롤러 계약: 정상/`INVALID_AMOUNT`/`MEMBER_NOT_FOUND`) PASS · **Level 3(DB 락·동시성, `PointServiceConcurrencyTest`)** PASS — 같은 회원에 스레드풀(10)로 30개 동시 충전 요청 후 최종 잔액이 정확히 `30 × 1,000 = 30,000`, `PointHistory` 건수도 정확히 30건(lost update 없음) · Level 5(로컬 기동) PASS · Level 6(실제 HTTP) PASS.
- 증거(API 샘플, `./gradlew bootRun` + `curl`):
  ```
  POST /api/points/charge {"memberId":12,"amount":5000}
  → 200 {"code":"SUCCESS","data":{"memberId":12,"balance":35000}}
  POST /api/points/charge {"memberId":12,"amount":3000}   (같은 회원 재충전 — 누적 확인)
  → 200 {"code":"SUCCESS","data":{"memberId":12,"balance":38000}}
  POST /api/points/charge {"memberId":3,"amount":0}
  → 400 {"code":"POINT_001","message":"충전 금액은 0보다 커야 합니다"}
  POST /api/points/charge {"memberId":3,"amount":-100}
  → 400 {"code":"POINT_001","message":"충전 금액은 0보다 커야 합니다"}
  POST /api/points/charge {"memberId":9999,"amount":1000}
  → 404 {"code":"MEMBER_001","message":"존재하지 않는 회원입니다"}
  ```
  - 비고: 로컬 개발용 docker MySQL은 이 기능 이전부터 누적된 데이터라, `DataSeeder`가 최초 1회(테이블이 비어 있을 때만) 실행된 시점의 회원(예: id=7)에는 이번 변경 이전이라 `Point`가 없다 — 그 회원으로 충전하면 `MEMBER_001`이 잘못 뜨는 것처럼 보인다. Attempt 1 시점엔 이를 "코드 버그 아님"으로 판단했으나, **Attempt 2(자체 리뷰)에서 이 판단이 틀렸음이 확인됨** — 실제 존재하는 회원에게 잘못된 404를 주는 진짜 버그였다.

## Attempt 2 — 2026-07-16  ✅ PASS
- 시도: PR #23 자체 리뷰(`/code-review --comment`, 8개 앵글 서브에이전트 + 후보 8건 중 6건 1-vote 검증 → 5건 CONFIRMED). 발견 순위:
  1. **CONFIRMED**(최고 심각도) — `PointService.charge`가 "Point 락 조회 실패 = 회원 미존재"로 단정. `DataSeeder`는 `memberRepository.count() == 0`일 때만 실행되므로, 이 변경 이전에 이미 존재하던 회원(정확히 위 비고의 id=7 케이스)은 실제 회원인데도 404를 받음 — Attempt 1의 "코드 버그 아님" 판단이 틀렸다는 근거이기도 함. **수정**: `PointService`에 `MemberRepository` 주입, Point 조회 실패 시 `memberRepository.existsById`로 실제 존재를 확인 후 없으면 `MEMBER_NOT_FOUND`, 있으면 그 자리에서 `Point`를 생성해 계속 진행(불변식이 깨진 회원에 대한 방어적 백필).
  2. **CONFIRMED** — `Point.charge`의 `balance += amount`에 상한 검증이 없어 `Long` 오버플로우 시 조용히 음수로 wrap. **수정**: `Math.addExact`로 교체(오버플로우 시 예외).
  3. **CONFIRMED** — `amount` null/누락 시 `@NotNull`이 서비스보다 먼저 가로채 `COMMON_001`을 반환하는데 `docs/api/point.md` 에러 표에 없었음(서비스의 `amount == null` 분기는 실 HTTP 경로에서 도달 불가한 죽은 코드). **수정**: `docs/api/point.md` 에러 표에 `COMMON_001` 행 추가.
  4. **CONFIRMED** — `ADR-004`가 "Point는 첫 충전 시 lazy 생성"이라는, 이번 PR로 이미 뒤집힌 근거를 FK 방향 결정에 그대로 쓰고 있었음. **수정**: 해당 문단을 "핵심 엔티티가 부가 상태를 몰라야 한다" 논리로 재작성하고 시드 방식 변경 경위를 병기.
  5. **CONFIRMED**(테스트 품질) — `PointServiceConcurrencyTest`가 `latch.await(30s)`의 반환값(타임아웃 여부)을 버려서, 타임아웃과 진짜 lost update 버그를 구분 못 함. **수정**: 반환값을 `assertThat(...).isTrue()`로 명시 검증.
  - PR 인라인 코멘트 5건 모두 반영 후 push, 각 스레드 resolve 예정.
- 결과: `./gradlew test`(전체, 실 MySQL 대상) 25건 전부 PASS(신규 1건: 회원 존재·Point 없음 케이스).
- 검증 레벨: Level 1(단위+회귀 전체) PASS. **Level 6(실제 HTTP) 재검증**으로 수정이 실제로 문제를 고쳤는지 재현 확인:
  ```
  POST /api/points/charge {"memberId":7,"amount":5000}   (레거시 Point 없는 실제 회원 — 수정 전엔 404였음)
  → 200 {"code":"SUCCESS","data":{"memberId":7,"balance":5000}}
  POST /api/points/charge {"memberId":7,"amount":2000}   (같은 회원 재충전 — Point가 잘 생성됐는지 누적 확인)
  → 200 {"code":"SUCCESS","data":{"memberId":7,"balance":7000}}
  POST /api/points/charge {"memberId":999999,"amount":1000}   (진짜 없는 회원 — 여전히 404 맞는지 확인)
  → 404 {"code":"MEMBER_001","message":"존재하지 않는 회원입니다"}
  POST /api/points/charge {"memberId":7}   (amount 누락 — COMMON_001 확인)
  → 400 {"code":"COMMON_001","message":"amount 널이어서는 안됩니다"}
  ```
  세 시나리오 모두 기대대로 동작 확인.

## Attempt 3 — 2026-07-16  ✅ PASS
- 시도: 게이트 B 대기 중 사용자와의 코드 설명·리뷰 대화에서 나온 피드백을 반영:
  1. `PointHistory`/`Point`를 `repository.save(new X(...))`처럼 인라인 생성하던 걸 지역 변수로 분리(`PointService`) — 이 프로젝트 서비스 코드에서 쓰던 스타일이 아니라는 지적. → [[feedback_no_inline_new_in_save]]
  2. `PointChargeRequest`(`@Getter @Setter @NoArgsConstructor`)를 Java `record`로 전환. 사용자가 "다른 DTO들도 다 record로 바꿔달라"고 확장 지시해 `PointChargeResponse`·이미 merge된 `MenuResponse`(#3)까지 함께 전환, 사용처(`PointController`, 관련 테스트) 갱신. `docs/code-convention.md`에 "요청·응답 DTO — record" 섹션 신설. → [[feedback_dto_record]]
  3. `@NotNull`에 `message`를 명시하지 않아 Hibernate Validator 기본 메시지가 JVM 로케일에 따라 달라지는 문제 발견(사용자 질문 계기) → `message = "필수입니다"`로 명시. `GlobalExceptionHandler`가 필드명을 이미 앞에 붙이므로 `message`엔 필드명을 넣지 않기로 함(처음엔 넣었다가 `"amount amount는 필수입니다"`로 중복되는 걸 발견해 수정).
  4. record 컴포넌트·정적 팩토리 인자 줄바꿈 규칙을 "2개 이상이면"에서 "필드 개수 무관 항상"으로 명확화(사용자 지시).
  5. 사용자 질문에 답하며 `Point.charge()`의 검증 로직을 엔티티로 옮기자는 제안 검토 — `CoffeeOrderException`/`ErrorCode`가 API 응답 전용 타입이라 엔티티가 웹 계층에 의존하게 되는 문제, 그리고 검증을 락 획득보다 뒤로 미루게 되는 손해를 근거로 반대 의견 제시, 사용자가 현재 구조 유지로 확정.
- 결과: `./gradlew test`(전체, 실 MySQL 대상) 25건 전부 PASS. 실제 HTTP로 record 바인딩(Jackson 네이티브)·수정된 검증 메시지(`"amount 필수입니다"`, 필드명 중복 없음) 재확인.
- 검증 레벨: Level 1(단위+회귀 전체) PASS · Level 6(실제 HTTP) PASS.
- 커밋: `7ee75fb`(스타일) · `88ce6b2`/`272dff1`/`6074804`/`dff8da6`(DTO record + 컨벤션).
