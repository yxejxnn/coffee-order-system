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
  - 비고: 로컬 개발용 docker MySQL은 이 기능 이전부터 누적된 데이터라, `DataSeeder`가 최초 1회(테이블이 비어 있을 때만) 실행된 시점의 회원(예: id=7)에는 이번 변경 이전이라 `Point`가 없다 — 그 회원으로 충전하면 `MEMBER_001`이 잘못 뜨는 것처럼 보이지만 실제로는 "레거시 시드 데이터의 상태"일 뿐, 코드 버그 아님(테스트로 새로 생성한 회원 id=12는 정상 동작). 신규(빈) DB로 부팅하면 시드된 모든 회원이 처음부터 `Point`를 가지므로 이 현상 자체가 재현되지 않는다.
