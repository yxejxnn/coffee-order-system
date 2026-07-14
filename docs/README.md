# docs — 컨텍스트 라우터 (문서 인덱스)

이 폴더의 문서들이 각 작업 규칙의 **원천(SSOT)**이다. `AGENTS.md`는 실행 흐름만 정하고, **"무슨 일에 어느 문서를 볼지"는 여기서** 찾는다.

> ⚠️ `docs/` 전체를 훑지 않는다. 지금 하는 일에 맞는 문서 **3~5개만** 열고, 충돌·공백이 생길 때만 한 단계 확장한다. (컨텍스트 폭주가 "막힘"의 주원인이다.)

| 이럴 때 | 읽을 문서 |
|---------|-----------|
| Clarify — 요구 애매·정책 공백 | `open-questions.md` · (구조적이면) `adr/` |
| Plan — 계획 수립 | `workflow/plan-guide.md` · `agent-mistakes.md` |
| 이슈 생성 · PR · 검토 루프 | `workflow/issue-pr-guide.md` |
| Generate — 구현 | `workflow/generate-guide.md` · `code-convention.md` |
| Evaluate — 평가·검증 레벨·루프 규칙 | `workflow/evaluate-guide.md` |
| 실행 모드 · 브랜치 · git | `branch-guide.md` |
| 개발문서(ongoing/design/changes) | `dev-doc-guide.md` |
| 개발 로그(성공/실패 매 시도) | `logs-guide.md` |
| 설계 근거·의사결정(ADR) | `adr/` (README부터) |
| API / 테이블 / 정책 명세 | `api/` · `db/` · `policy/` (각 README부터) |
| 커밋 메시지 | `commit-convention.md` |
| 전체 진행 현황 | `dev/ongoing/` (폴더 자체가 현황판) |

- 각 문서가 **해당 규칙의 SSOT**다. 규칙이 충돌하면 원천 문서가 이긴다.
- 이 표에 없다는 이유로 `docs/` 하위를 전부 훑지 않는다. 필요한 정본을 **한 단계씩** 확장한다.
