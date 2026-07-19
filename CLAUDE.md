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
- **머신 특화 설정**: `cp .claude/settings.local.json.example .claude/settings.local.json` 후 DB 비번 등 개인 값을 채운다(이 파일은 gitignore). git 훅 재설치는 `docs/branch-guide.md` 참고.
- **자체 리뷰(`/code-review --comment`) 코멘트 스레드 처리** (`docs/workflow/issue-pr-guide.md` 5단계의 실행 규칙): 발견사항이 **엔지니어링 판단 범위**(대부분의 코드 리뷰 코멘트 — 트레이드오프·컨벤션·효율·범위 판단 등)면 Claude가 **직접 결론 낸다**: 고쳐서 커밋·push 후 답글 남기고 resolve하거나, 안 고치기로 확정했으면 근거를 답글로 남기고 **역시 resolve한다**. "안 고침"과 "미해결"은 다르다 — 판단을 끝냈으면 resolve한다. **"게이트 B로 넘긴다"·"사람 검토 필요"라고 스레드에 적어두고 넘어가지 않는다** — PR을 사람에게 검토를 떠넘기는 곳으로 쓰지 않는다(2026-07-17 사용자 정정: "사람한테 개발적인 부분에서 검토를 맡는게 아니라 니가 판단해서 다 해결하고... 내가 다 검토해서 할거면 그냥 처음부터 내가 코딩 다 하지 왜 하네스 만들어서 에이전트를 쓰겠냐"). 정말로 Claude의 판단 범위를 벗어나는 항목(모호한 제품/범위 요구, 되돌리기 어려운 조치, 사용자가 명시적으로 유보한 것)만 **그 자리에서 바로 `AskUserQuestion`으로 직접**(보기/선택지 형태로) 묻는다 — PR 코멘트에 적어두는 걸로 대신하지 않는다. 이렇게 정리한 뒤 게이트 B에서 사람에게 남는 건 merge 여부 판단뿐이어야 한다.
- **채팅 응답은 한국어로.** 사용자가 한국어로 대화하면 Claude의 채팅 응답(설명·요약·상태 업데이트)도 전부 한국어로 쓴다 — 중간에 영어 헤더/불릿으로 전환하지 않는다(2026-07-17 사용자 정정: "영어로 씨부리면 내가 어케 알아?"). 코드·커밋 메시지·PR 본문·`docs/`는 이 저장소의 기존 한국어 컨벤션을 그대로 따른다(별도 지시가 아니라 원래도 한국어).

## 자주 쓰는 명령

```bash
./gradlew test            # Evaluate의 계산적 평가
./gradlew compileJava     # Generate 후 빠른 컴파일 확인
ls docs/dev/ongoing/      # 진행 중 작업 현황판
```
