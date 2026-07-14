# 재활용 하네스 틀 (Reusable Harness)

AI 코딩 에이전트(Claude Code / Codex 등)가 **막힘없이, 추상적으로 얼버무리지 않고** 백엔드 기능을 개발하도록 잡아주는 **문서 + 자동화 골격**이다. 특정 도메인에 묶여 있지 않아 **어떤 (Spring/Gradle 계열) 프로젝트에도 얹어서 재사용**할 수 있다.

## 이 하네스가 하는 일 (한 줄)

> "무엇을·어떻게·어떻게 검증할지"를 **문서로 먼저 확정(Plan)** → **사람이 승인** → **그대로 구현(Generate)** → **테스트·규칙으로 판정(Evaluate)** → 통과할 때까지 루프.

애매한 지점은 에이전트가 **추측 대신 질문**하게 만들고, 각 단계의 산출물(계획·명세·로그·design)이 다음 단계의 입력이 되어 **컨텍스트가 끊겨도 이어서** 일할 수 있다.

## 폴더 구조

```
.
├── AGENTS.md                  # 공통 진실의 원천 — 실행 흐름·규칙 (도구 무관, 라우터는 docs/README.md)
├── CLAUDE.md                  # Claude Code 전용 진입점 (Plan Mode = 휴먼 게이트)
├── README.md                  # (이 파일) 하네스 소개 + 재사용 셋업
├── .claude/
│   ├── settings.json          # 훅 + 권한 (팀 공유)
│   ├── settings.local.json.example  # 개인/머신 특화 권한 예시 (복사해서 사용)
│   ├── agents/                # planner · generator · evaluator (역할 분리 서브에이전트)
│   ├── hooks/                 # log-activity(활동로그) · require-log(로그 누락 차단)
│   └── workflows/             # run-autonomous.sh(런처) · plan-generate-evaluate-loop.js(자율 루프)
├── .harness/
│   └── hooks/                 # pre-commit·pre-push(main·dev 보호) + install.sh (공유 가능한 git 훅)
└── docs/
    ├── README.md             # ★ 컨텍스트 라우터 (문서 인덱스) — 무슨 일에 어느 문서를 볼지
    ├── workflow/              # plan-guide · generate-guide · evaluate-guide(검증 레벨 포함)
    ├── adr/                   # ★ 아키텍처 의사결정 기록 — "왜 이 설계인가" (README + 템플릿)
    ├── open-questions.md      # ★ Clarify 산출물 — 미결정/튜터 질문 로그
    ├── agent-mistakes.md      # ★ 반복 실수 학습 루프 (Plan 단계에서 읽음)
    ├── dev-doc-guide.md       # 개발문서(ongoing/design/changes) 작성법
    ├── logs-guide.md          # 실행 이력·증거 로그 작성법
    ├── branch-guide.md        # git 브랜치 모델
    ├── code-convention.md     # 코드 규칙 (루트 패키지만 프로젝트별로 채움)
    ├── commit-convention.md   # 커밋 메시지 규칙
    ├── api/                   # API 계약 명세 (SSOT)
    ├── db/                    # 테이블 스키마 명세 (SSOT)
    ├── policy/                # 공유 비즈니스 규칙 (SSOT)
    └── dev/ongoing/           # 진행 중 개발문서 큐 (= 현황판)
```

## 핵심 개념 6가지

1. **Clarify → Plan → (승인) → Generate → Evaluate → PR → (사람 검토)** — 애매하면 Clarify에서 멈춰 질문/ADR로 확정하고, 승인 전엔 코드 한 줄도 안 짠다. 이슈 단위로 순차 진행하며, PR을 올린 뒤 **사람 검토를 기다린다**(merge는 사람만).
2. **고도(altitude) 경계** — Plan은 계약·방향만, Generate가 구현 디테일. 서로 침범 안 함 → 계획이 추상적으로 새거나, 구현이 멋대로 설계를 바꾸는 걸 막는다.
3. **문서 = 진실의 원천(SSOT)** — API/DB/정책/design/ADR을 코드보다 먼저 문서로 정의하고 갱신. 위치가 곧 상태(`ongoing/`=진행 중, `changes/`=완료).
4. **로그 = 피드백 센서** — 성공/실패 매 시도를 `docs/logs/`에 남기고, 반복 실수는 `agent-mistakes.md`로 승격해 다음 작업이 같은 함정을 피한다.
5. **ADR = 설계 논증** — "왜 이 구조인가"를 대안·트레이드오프와 함께 남긴다. 실전/채용 과제의 핵심 평가 포인트.
6. **검증 레벨 = 얼버무림 방지** — "테스트 통과"가 아니라 "Level 0~6 중 어디까지 실제로 검증했나"를 명시. Mock 통과로 완료를 주장하지 못한다.

> ⚙️ **컨텍스트 라우터**: 에이전트는 `docs/`를 전부 읽지 않고, 지금 하는 일에 맞는 3~5개만 연다(`docs/README.md`). 컨텍스트 폭주가 "막힘"의 주원인이라 이 규칙이 중요하다.

---

## 새 프로젝트에 적용하기 (셋업 체크리스트)

이 폴더의 **`AGENTS.md` · `CLAUDE.md` · `.claude/` · `.harness/` · `docs/`**를 대상 프로젝트 루트에 복사한 뒤:

- [ ] **1. 프로젝트 값 채우기** — `AGENTS.md`의 "프로젝트 개요" 블록(`{{프로젝트명}}` 등)을 채운다.
- [ ] **2. 루트 패키지** — `docs/code-convention.md`의 `{{루트_패키지}}`를 실제 패키지로 바꾼다.
- [ ] **3. 빌드 명령 확인** — `AGENTS.md`·`CLAUDE.md`의 `./gradlew ...`가 프로젝트와 맞는지 확인(Maven이면 교체).
- [ ] **4. 개인 권한 설정** — `cp .claude/settings.local.json.example .claude/settings.local.json` 후 DB 비번 등 머신 특화 값을 채운다. (이 파일은 gitignore)
- [ ] **5. git 브랜치 준비** — `git switch -c dev` (모든 작업의 출발점).
- [ ] **6. main·dev 보호 훅 설치** — `sh .harness/hooks/install.sh` (pre-commit·pre-push. `.git/hooks`는 클론 시 사라지므로 재설치 필요).
- [ ] **7. 첫 작업 시작** — Claude Code에서 "AGENTS.md 따라 `{개념}/{기능}` 계획부터 세워줘" 로 Plan을 요청한다.

> 대부분의 문서(가이드·컨벤션)는 **schedule를 예시로** 설명한다. 예시는 그대로 두고 패턴만 참고하면 된다 — 실제 도메인 문서는 작업하며 `docs/api/`·`docs/db/`·`docs/dev/`에 쌓인다.

## 사용 흐름 (기본 모드)

```
1. 사용자: "schedule/create 기능 계획 세워줘"
2. planner: gh issue 생성 + docs/dev/ongoing/schedule-create.md(이슈 #N 링크, +필요시 api/db 명세) → "승인해 주세요"
3. 사용자: 계획 검토 후 승인  ← 게이트 A
4. generator: feature/{이슈번호}-{기능}에서 구현 + 커밋(기능 단위) + docs/logs 시도 기록
5. evaluator: ./gradlew test + 규칙 준수 판정
6. push(feature) → PR(base: dev, Closes #N) 올리고 멈춤  ← 게이트 B (사람 검토)
7. 사람 검토: 코멘트 → 수정→push→재검토 (반복) → 사람이 merge
8. merge 후: design.md 갱신 + ongoing→changes 이동 → 다음 이슈
```

> 실패 루프: 같은 접근=Generate 재시도 / 접근 변경=Plan 재승인, 3회 초과 시 중단·보고.

자율 모드(작고 신뢰되는 작업, 게이트 생략): `sh .claude/workflows/run-autonomous.sh schedule/create`

## 언제 더 무거운 하네스로 승격하나

이 틀은 **개인·소규모 과제에 맞춘 경량판**이다. GitHub 이슈·PR·사람 검토는 **기본 흐름에 포함**돼 있고(`docs/workflow/issue-pr-guide.md`), 팀/장기로 커지면 아래를 단계적으로 더한다:

- **CI 게이트** — PR에서 빌드·테스트를 자동 실행해 merge 전 최종 판정(GitHub Actions).
- **Issue/PR 템플릿** — 이슈·PR 본문 필수 필드를 템플릿으로 표준화.
- **실행 모드 3분화** — 위험도별 `SOLO / STANDARD / STRICT`로 역할 구성을 조절(문서 오타에 풀 사이클을 돌리지 않게).
- **역할 분리** — Review와 QA를 독립 에이전트로 나눠 fresh 컨텍스트 교차 검증.
- **기계적 게이트** — evidence 파일·PR 본문 필수 필드·문서 링크 존재를 스크립트로 강제(`harness_gate` 류).

> 참고 사례: 이 틀의 "졸업판"에 해당하는 구성이 실제로 존재한다(동일 DNA를 채용 제출용으로 확장). 필요해지면 그 방향으로 한 겹씩 올린다.
