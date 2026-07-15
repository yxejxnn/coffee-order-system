# CLAUDE.md

> Claude Code 전용 진입점. **작업 방식의 원천은 `AGENTS.md`다** — 이 파일은 Claude Code에 특화된 것만 덧붙인다.

## 먼저 읽을 것

1. **`AGENTS.md`** — 실행 흐름(Plan→Generate→Evaluate), 휴먼 게이트, 실행 모드, 규칙. **모든 작업 전 필독.**
2. **`docs/README.md`** — 컨텍스트 라우터(문서 인덱스). 각 단계에 들어가면 여기가 가리키는 문서(`docs/...`)를 그때 읽는다.

## Claude Code 특화 사항

- **휴먼 게이트 = Plan Mode로 강제한다.** 기본 모드에서는 계획을 세울 때 Plan Mode로 진입해, 사용자가 계획을 승인(Exit Plan)하기 전에는 파일을 수정/생성하지 않는다. → 이것이 AGENTS.md의 "휴먼 게이트"를 기계적으로 보장한다.
- **서브에이전트 역할 분리**: `.claude/agents/`의 `planner`(계획·코드금지) / `generator`(구현) / `evaluator`(테스트·판정)를 단계별로 사용한다. 루프 조율(재시도·재계획 판단)은 메인 세션이 한다.
- **자율 모드**는 반드시 런처로만: `sh .claude/workflows/run-autonomous.sh {개념}/{기능}`. 메인 세션에서 워크플로우를 직접 부르지 않는다(Guard가 막는다).
- **훅**: `.claude/settings.json`이 PreToolUse(활동로그)·Stop/SubagentStop(로그 누락 차단)을 건다. 코드(`src/`)를 바꾸면 `docs/logs/`에 시도 기록을 남겨야 종료된다.
- **자체 리뷰(`/code-review --comment`) 코멘트 스레드 처리** (`docs/workflow/issue-pr-guide.md` 5단계의 실행 규칙): 반영한 코멘트는 커밋·push 후 **Resolve conversation**으로 닫는다. 반영하지 않기로 한 코멘트(사용자의 기존 결정과 상충 등, 사람 판단 필요)는 **resolve하지 않고** 왜 안 고쳤는지 그 스레드에 **답글**만 남긴 채 게이트 B로 넘긴다 — resolve는 "고쳤다"는 뜻이라, 안 고친 걸 resolve하면 사람이 최종 검토에서 놓친다.

## 자주 쓰는 명령

```bash
./gradlew test            # Evaluate의 계산적 평가
./gradlew compileJava     # Generate 후 빠른 컴파일 확인
ls docs/dev/ongoing/      # 진행 중 작업 현황판
```
