# 이슈 완료 체크리스트 (issue-completion-checklist)

한 이슈가 "완료"되기 전에 확인하는 항목이다. **계획 단계에서 이 이슈에 해당하는 항목을 골라 이슈/PR에 넣고**, PR 올리기 전에 모두 확인한다.
(PR 템플릿 `.github/PULL_REQUEST_TEMPLATE.md`의 "완료 전 체크리스트"가 이 문서를 가리킨다.)

---

## 계획 단계에서 정해 이슈에 기재 (WHAT)

- [ ] **완료(평가) 기준** — 어떤 테스트/확인으로 완료를 판정하는가.
- [ ] **필요 검증 레벨** — Level 5(로컬 기동)·Level 6(실제 HTTP) 필요 여부 + 이유. (`docs/workflow/evaluate-guide.md`)
- [ ] **영향 범위·공유 구현 의존성** — 다른 이슈와 공유되는 구현(엔티티·공통 서비스)이 있으면 순서·의존성 파악.

> 위 세 항목이 "계획 단계에서 만드는" 부분이다. PR은 이 결정을 그대로 복사한다.

## 구현·검증 (PR 전 확인)

- [ ] **계산적 평가** — 대상 기능 테스트 통과(`./gradlew test --tests "*{기능}*"`) + 전체 회귀 smoke.
- [ ] **추론적 평가** — 승인된 계획 범위대로 구현(임의 확장 없음), 관련 정책(`docs/policy/`) 위반 없음.
- [ ] **코드 컨벤션 준수** (`docs/code-convention.md`) — 컨트롤러 `ResponseEntity<ApiResponse<...>>`, 응답 DTO(`final`+`@RequiredArgsConstructor`+정적 팩토리 `from`), 엔티티 생성자 직접 작성 등.
- [ ] **검증 레벨 실제 이행** — 계획에서 정한 레벨을 실제로 밟았다(Mock 통과만으로 "완료" 주장 금지). Level 5/6 결정대로 검증.

## 기록·문서

- [ ] **개발 로그** — 이번 시도(성공/실패)를 `docs/logs/`에 기록. (`docs/logs-guide.md`)
- [ ] **design.md 갱신**(SSOT) — **push 전에** 끝내 PR에 포함시킨다 (머지 후로 미루지 않음). (`docs/dev-doc-guide.md`)
- [ ] **(merge 후)** ongoing→changes 채번 이동. (`docs/dev-doc-guide.md`)

## git · 프로세스

- [ ] **feature 브랜치**에서 작업 (main/dev 직접 커밋·push 안 함).
- [ ] 커밋은 **기능 구현 단위**.
- [ ] **자체 리뷰(`/code-review`)** 통과 후 발견사항 반영.
- [ ] **Merge·Issue close는 사람 승인 대기** (스스로 merge하지 않음).
- [ ] (merge 후) 사용한 **feature 브랜치 삭제**(로컬+원격).

## 정직성

- [ ] **미검증 항목·남은 위험**을 PR에 솔직히 기재.
- [ ] 통과하지 않은 것을 "통과"라고 말하지 않는다.
