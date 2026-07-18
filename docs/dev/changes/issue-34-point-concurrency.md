# 포인트 최초 생성 경로 동시성 구멍 제거

대상: point/charge
이슈: [#34](https://github.com/yxejxnn/coffee-order-system/issues/34)
담당: Claude

## 배경 / 요구
`PointService.getLockedPoint()`는 비관적 락 조회가 비면(=그 회원의 `Point`가 아직 없으면) `createPointForExistingMember`로 폴백해 회원 존재만 확인하고 그 자리에서 `Point`를 insert한다. 이 폴백엔 락이 없어, 같은 회원(Point 없는 불변식 파손 상태)이 동시에 2번 첫 충전을 요청하면 둘 다 insert를 시도해 `uk_member_id` 위반 → `DataIntegrityViolationException` → 500이 난다. `docs/dev/point/charge/design.md`에 이미 알려진 한계로 기록돼 있던 것.

## 설계
초안(같은 트랜잭션 안에서 insert 실패를 잡아 재조회)은 구현 중 두 종류의 MySQL 데드락으로 이어져(상세: `docs/logs/point/charge/002-concurrency-fix.md`) 최종적으로는:
1. `charge`/`use` 트랜잭션을 `READ_COMMITTED`로 낮춰, 존재하지 않는 행에 대한 `SELECT ... FOR UPDATE`의 갭 락을 없앤다.
2. `Point` 생성을 `PointBootstrapService.ensurePointExists`(`REQUIRES_NEW`, 별도 트랜잭션)에 위임해, insert 시도(성공/duplicate-key 실패 모두)가 완전히 끝나고 커밋/롤백된 뒤에야 바깥 트랜잭션이 새로 락을 건다.
3. `ensurePointExists`는 예외를 안에서 삼키지 않고 그대로 던져 Spring이 정상 롤백하게 하고, 호출자(`PointService`)가 `DataIntegrityViolationException`을 잡아 무시한 뒤 재조회한다.

폴백 경로 자체를 없애는 대안(완료조건의 다른 선택지)은 시드 이전 회원에 대해 `MEMBER_NOT_FOUND` 회귀를 만들어 기각(`docs/dev/changes/issue-4-point-charge.md`에서 고친 버그가 되살아남).

## 관련 결정·질문
- `docs/dev/point/charge/design.md` (이 변경으로 갱신 필요 — "이론적으로 남아있다"던 한계가 해소됨)
- [ADR-001](../../adr/ADR-001-포인트-동시성제어.md)

## 태스크
- [x] `PointService.createPointForExistingMember` 수정, `PointBootstrapService` 신설
- [x] `PointServiceConcurrencyTest`에 "Point 없는 회원 동시 첫 충전" 케이스 추가
- [x] `docs/dev/point/charge/design.md` 갱신(SSOT)

## 평가(통과) 기준
- 신규 동시성 테스트: N개 동시 첫 충전 요청 → 예외 없이 완료, 최종 잔액 == N×amount, `Point` 행 정확히 1개, `PointHistory` 정확히 N건
- 검증 레벨: Level 1(단위+회귀) · Level 3(락·동시성 통합, 실제 MySQL 대상)
