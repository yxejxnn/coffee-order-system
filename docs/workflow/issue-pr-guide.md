# 이슈·PR 가이드 (issue-pr-guide)

개발은 **이슈 단위로 순차** 진행한다. 계획 단계에서 전체를 **잘게 쪼개 이슈를 한꺼번에** 만들고, 그다음 **이슈 하나씩** 개발 → PR → **Claude 자체 리뷰·수정** → **머지 직전 사람 최종 검토·merge** → 브랜치 삭제 → 다음 이슈.
Claude가 PR을 스스로 리뷰·수정해 통과시킨 뒤 **머지 직전에 멈추고**, 사람의 최종 검토를 기다린다. **merge는 사람만** 한다.
(이슈 쪼개기 원칙 — 잘게 / WHAT은 미리 상세 / HOW는 집을 때 — 은 `docs/workflow/plan-guide.md` "이슈 쪼개기" 참조.)

> 이 문서는 실행 흐름(AGENTS.md)의 **이슈 생성 · Push+PR · 검토 게이트**를 `gh` 명령과 함께 규정한다.
> 브랜치 보호·격리는 `docs/branch-guide.md`, 커밋 형식은 `docs/commit-convention.md`가 원천이다.

---

## 전체 흐름

```
0. [Plan] 전체를 잘게 쪼개 이슈 여러 개를 한꺼번에 생성 (WHAT 상세 / HOW·공유구현은 미룸)
   ══ 게이트 A: 이슈 쪼개기(로드맵) + 계획을 사람이 승인 ══

── 이하 이슈 하나씩 반복 ──
1. 이슈 집기 → ongoing 문서에 그 이슈의 설계(HOW)를 지금 채움
2. feature/{이슈번호}-{기능} 브랜치에서 구현 + 커밋   ← Generate
3. 테스트·규칙 판정 + docs/logs 기록                ← Evaluate
4. push(feature) → PR 생성(base: dev, "Closes #N")
5. Claude 자체 리뷰(/code-review) → 발견사항 수정 → 재리뷰   (통과=approve까지 반복)
6. 리뷰 통과 → 머지 직전에 멈춤
   ══ 게이트 B: 사람 최종 검토 → 승인 → merge ══
7. merge 후: 이슈 자동 close · design.md 갱신 · ongoing→changes 이동 · feature 브랜치 삭제 → 다음 이슈(1로)
```

---

## 1. 이슈 쪼개기 (Plan 단계 — 한꺼번에)

작업을 **잘게 쪼개** 이슈 여러 개를 계획 단계에 **한꺼번에** 만든다. 각 이슈 본문엔 **WHAT(목표·완료조건·영향범위)을 상세히**, HOW(구현·공유구현)는 비운다. 상세 설계는 그 이슈를 집을 때 `docs/dev/ongoing/` 문서에 채운다 (이슈=추적, 문서=설계).

```bash
gh issue create \
  --title "{개념}/{기능}: 한 줄 요약" \
  --body "목표 / 완료조건 / 영향범위. 상세 설계: docs/dev/ongoing/{작업}.md"
# 쪼갠 이슈마다 반복. 발급된 #N 을 대응 ongoing 문서의 `이슈:` 필드에 적는다.
```

- 쪼개기 원칙(잘게 / WHAT 상세 / HOW·공유구현은 나중 / 목록은 지도)은 `docs/workflow/plan-guide.md` "이슈 쪼개기" 참조.
- ongoing 문서 작성법·`이슈: #N` 필드는 `docs/dev-doc-guide.md` 참조.
- **쪼갠 이슈 목록 + 계획 + (필요 시) 명세·ADR이 게이트 A에서 승인받을 대상**이다 (= 로드맵 검토).
- 앞 이슈 개발이 뒤 이슈를 무효화하면 그 이슈를 **수정/close**한다 (목록은 계약이 아니라 지도).

## 2~3. 이슈 집기 · 브랜치 · 구현 · 평가

이슈 하나를 집으면, **그 이슈의 ongoing 문서에 설계(HOW)를 지금 채운다** (앞 이슈에서 확정된 공통 구조를 반영). 그다음:

```bash
git switch dev && git switch -c feature/{이슈번호}-{기능}   # 예: feature/12-create
```

- `dev`에서 분기한다. 커밋은 **기능 구현 단위**(`docs/commit-convention.md`).
- 구현은 `docs/workflow/generate-guide.md`, 평가·검증 레벨은 `docs/workflow/evaluate-guide.md`.
- 커밋 본문에 `refs #N`(또는 관련 `changes/00X`)을 링크하면 추적성이 좋다.

## 4. Push + PR 생성 → **멈춤**

Evaluate 통과 후 feature 브랜치를 push하고 PR을 연다. `main`·`dev`로의 직접 push는 훅이 막는다.

```bash
git push -u origin feature/{이슈번호}-{기능}
gh pr create --base dev --head feature/{이슈번호}-{기능} \
  --title "{개념}/{기능}: 한 줄 요약" \
  --body "Closes #N

## 변경
- ...
## 검증
- Level x PASS (근거: docs/logs/...)"
```

- 본문에 **`Closes #N`**을 넣어 이슈를 연결한다 (merge 시 이슈 자동 close).
- PR을 올린 뒤 **바로 멈추지 않는다** — 다음 단계(자체 리뷰)로 이어간다.

## 5. 자체 리뷰 → 수정 (통과까지 반복)

PR을 올렸으면 **Claude가 먼저 스스로 코드 리뷰**를 돌려 발견사항을 고친다. 사람에게 넘기기 전에 Claude가 정리한다. 이 리뷰는 **역할이 분리된 절차**다 — 같은 세션이 검토와 수정을 모두 하더라도, 검토 결과는 "그 자리에서 바로 고치는 메모"가 아니라 **PR에 남는 실제 코멘트**여야 다음 단계(사람 최종 검토)에서 무엇을 지적받고 무엇을 고쳤는지 추적할 수 있다.

```bash
# 1) 검토: PR diff에 대해 코드 리뷰를 돌리고, 발견사항을 실제 PR 리뷰 코멘트로 남긴다
/code-review --comment

# 2) 수정: 남겨진 코멘트를 하나씩 반영 → 커밋 → push (같은 feature 브랜치 → PR 자동 갱신)
git push

# 3) 재검토: 위 1)을 다시 돌려 남은 발견사항이 있는지 확인 → 없으면 통과
```

- **검토(1) → 수정(2) → 재검토(3)를 발견사항이 없어질 때까지 반복**한다. 검토 단계를 생략하고 바로 고치거나, 코멘트 없이 "구두로" 리뷰 결과만 보고하고 넘어가지 않는다.
- 수정도 **매 시도를 `docs/logs/`에 기록**한다 (`docs/logs-guide.md`).
- 무한 반복 방지: 같은 방식으로 3회 실패하면 멈추고 사람에게 보고한다 (`docs/workflow/evaluate-guide.md` 루프 규칙).
- 자체 리뷰가 실제로 발견한 이슈 중 **사용자의 기존 결정과 상충하는 것**(예: 의도적으로 선택한 트레이드오프)은 Claude가 임의로 되돌리지 않고, 사람에게 확인받는다.

## 6. 머지 직전 멈춤 → 사람 최종 검토 (게이트 B)

자체 리뷰가 통과하면 **머지 직전에 멈추고** 사람의 최종 검토를 기다린다. **merge는 사람만** 한다 (`gh pr merge`는 권한에서 차단).

- 사람이 최종 검토 후 승인·merge → 이슈는 `Closes #N`으로 자동 close.
- 사람이 추가 수정을 요청하면 → **5단계(자체 리뷰·수정)**로 돌아가 반영 후 다시 머지 직전에서 멈춤.

## 7. merge 후 마무리 → 다음 이슈

merge가 완료되면 Claude는:

1. 대상 기능 **`design.md`를 최종 상태로 갱신**(SSOT).
2. ongoing 문서를 **`changes/00X`로 채번 이동**.
   - (상세: `docs/dev-doc-guide.md`, `docs/workflow/evaluate-guide.md` "통과 시 후속")
3. **사용한 feature 브랜치를 삭제**한다 (로컬 + 원격):
   ```bash
   git switch dev
   git branch -D feature/{이슈번호}-{기능}                 # 로컬 (원격에서 이미 merge됨)
   git push origin --delete feature/{이슈번호}-{기능}       # 원격 (GitHub 자동삭제 설정이 없으면)
   ```
4. 이슈 close 확인 → **다음 이슈로** 넘어간다.

---

## 규칙 요약

- **리뷰는 Claude가 먼저, merge는 사람만.** PR을 올리면 Claude가 `/code-review --comment`로 발견사항을 **실제 PR 코멘트로 남기고** 수정을 통과까지 돌린 뒤, **머지 직전에 멈춰** 사람의 최종 검토를 기다린다. `gh pr merge`는 권한에서 차단.
- **merge = 사람의 승인이 끝났다는 뜻.** merge 후 feature 브랜치를 삭제(로컬+원격)하고, 그 이슈는 닫힌 채로 **바로 다음 이슈로** 넘어간다 — 별도 확인 없이 이어서 진행.
- **쪼개기는 한꺼번에, 개발은 하나씩** — 이슈는 계획 단계에 전체를 잘게 쪼개 만들고(WHAT 상세/HOW는 나중), 개발·PR·승인은 한 이슈가 통과돼야 다음으로 넘어간다.
- push는 **feature 브랜치만**, `main`·`dev`는 커밋/push 차단(훅 강제, `docs/branch-guide.md`).
- PR base는 **`dev`**. `dev`가 안정되면 사람이 `dev`→`main`을 정리한다.
