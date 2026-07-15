# 개발문서 작성 가이드 (dev-doc-guide)

이 문서는 **개발문서를 어떻게 쓰고 어디에 두는지**에 대한 방법론이다.
에이전트는 개발문서(design.md, ongoing/…, changes/…)를 작성·수정할 때 **반드시 이 문서를 참고**한다.

> 큰 그림: 기능은 코드보다 먼저 **문서(진실의 원천)**로 정의하고, 작업하며 계속 갱신한다.
> 이 문서들은 레포에 상주하므로 도구(Claude Code / Codex 등)와 무관하게 공유된다.

---

## 폴더 & 이름 컨벤션

```
docs/
├── dev-doc-guide.md              ← (이 문서) 개발문서 작성 가이드
└── dev/
    ├── ongoing/                   ← 진행 중 개발문서 (전역 큐, 한눈에)
    │   └── {작업이름}.md            예: issue-12-schedule-create.md
    └── {개념}/{기능}/              예: schedule/create/
        ├── design.md             ← 진실의 원천(SSOT) = 현재 최종 상태
        └── changes/              ← 완료된 개발문서 (파일명 그대로, 누적)
            ├── issue-12-schedule-create.md
            └── issue-15-schedule-add-repeat.md
```

### "개념(concept)"으로 나누는 이유

최상위는 **도메인이 아니라 개념**으로 나눈다. 도메인(order, payment…)으로 나누면
여러 서브시스템에 걸치는 기능(예: **환불**은 order·payment·point에 걸침)이 "어디 소속?"에서 애매해진다.
개념 기반은 그런 cross-cutting한 것을 **자기 개념으로 독립**시킬 수 있다.

```
도메인 기반(경직):  order/refund?  payment/refund?   ← 하나 골라야 함 → 애매
개념 기반(유연):    refund/         ← 환불이 자기 개념으로 독립
```

- **개념** = 응집된 하나의 주제/기능 영역 (schedule, user, refund, notification…).
- cross-cutting한 것도 최상위 개념으로 승격할 수 있다.
- 주의: 개념 입도를 일관되게 유지한다(잡탕 방지). 문서 개념과 코드 모듈이 1:1이 아닐 수 있으니,
  `design.md`에 **관련 코드 위치**를 적어 갭을 메운다.

### 핵심 규칙
- **진행 중 작업 = `docs/dev/ongoing/`에 문서 한 장** (이슈 번호로 이미 고유하게 명명, `issue-N-{기능}.md`).
- **완료되면 = 해당 기능의 `changes/`로 파일명 그대로 이동**한다 (renaming/채번 없음 — ongoing 파일명이 이미 고유해 추가 번호가 불필요).
- **위치가 곧 상태다**: `ongoing/`에 있으면 진행 중, `changes/`에 있으면 완료. (별도 `상태:` 필드 없음)
- **개념·기능 = 폴더**로 표현한다 (파일명에 이름을 반복하지 않는다).
  - `docs/dev/schedule/create/design.md` ⭕ / `docs/dev/schedule/design-schedule-create.md` ❌
- 각 기능 폴더에는 **`design.md` 1개 + `changes/` 1개**가 짝으로 있다.

---

## ongoing/ — 진행 중 개발문서 (전역 큐)

- 새 작업을 시작하면(Plan 단계) **`docs/dev/ongoing/issue-{N}-{기능}.md`**를 만든다 (GitHub 이슈 번호로 고유하게 명명).
- 이 폴더 하나에 **모든 진행 중 작업이 모인다** → `ls docs/dev/ongoing/`로 조직 전체 업무량을 한눈에 본다.
- 이슈 번호로 이미 고유하므로 완료 시에도 **파일명을 바꾸지 않는다** (아래 changes/ 참고).

### ongoing 문서 템플릿

```markdown
# 일정 생성 기능

대상: schedule/create        <!-- 완료 시 이 기능의 changes/로 이동 -->
이슈: #12                     <!-- 연결된 GitHub Issue (Plan에서 gh issue create) -->
담당: 홍길동                  <!-- 누가 작업 중인지 (가시화용) -->

## 배경 / 요구
<!-- 왜 이 작업을 하는가 -->

## 설계
<!-- 어떻게 접근하는가 -->

## 관련 결정·질문
<!-- 관련 ADR(docs/adr/), 미결정 질문(docs/open-questions.md) 링크 -->

## 태스크
- [ ] ...

## 평가(통과) 기준
<!-- 어떤 테스트/확인으로 완료를 판정하는가 + 필요 검증 레벨(Level 5 로컬기동·Level 6 실제 HTTP 등) -->
```

---

## design.md — 진실의 원천(SSOT)

- 그 기능이 **지금 어떻게 동작하는지**(현재 최종 상태)만 담는다. 히스토리·태스크·제안은 담지 않는다.
- 기능이 바뀌면 **덮어써서** 항상 최신 상태를 유지한다 (파일이 계속 커지지 않는다).
- "이 기능 지금 어떻게 동작해?"의 답은 **항상 design.md**다.
- design.md와 changes/가 어긋나면 → **design.md가 이긴다** (changes/는 경위 기록일 뿐).

### design.md 템플릿

```markdown
# {기능명} — Design

## 개요
<!-- 이 기능이 무엇을 하는가 (현재형 서술) -->

## API / 인터페이스
<!-- 이 기능이 쓰는 엔드포인트. 상세는 docs/api/{개념}.md 참조 -->

## 데이터 모델
<!-- 이 기능이 쓰는 테이블. 상세는 docs/db/{테이블}.md 참조 -->

## 규칙 / 검증
<!-- 비즈니스 규칙, 유효성 검증 (관련 정책은 docs/policy/ 참조) -->
```

> API·테이블 **상세 명세는 `docs/api/`·`docs/db/`가 원천**이다. design.md는 "이 기능이 무엇을 쓰는지" 참조만 한다 (중복 방지).

---

## changes/ — 완료된 개발문서

- 작업이 완료되면(Evaluate 통과) `ongoing/`의 문서를 해당 기능의 `changes/`로 **파일명 그대로 옮긴다**.
  - 예: `ongoing/issue-12-schedule-create.md` → `docs/dev/schedule/create/changes/issue-12-schedule-create.md`
  - **채번하지 않는다** — ongoing 파일명이 이미 이슈 번호로 고유하므로 별도 번호 부여가 불필요하고, 이름이 바뀌면 이슈와의 연결이 헷갈린다.
- 이동은 **`mv`**를 쓴다 (`git mv`는 아직 커밋 안 된 untracked 파일에선 실패한다). git은 삭제+추가로 보지만 문서 이동이라 무방하다.
- changes/ 문서는 완료된 기록이므로 이후 **고치지 않는다** (누적 아카이브).

---

## 워크플로우 바인딩 (AGENTS.md 실행 흐름과 연결)

| 실행 단계 | 문서 작업 |
|-----------|-----------|
| **Clarify** | 미결정 질문 → `docs/open-questions.md`, 구조적 결정 → `docs/adr/` ADR 초안 |
| **Plan** | **GitHub Issue 생성**(`gh issue create`) + `docs/dev/ongoing/{작업}.md` 생성 (대상·**이슈 #N**·담당·배경·설계·태스크·평가기준·검증레벨) |
| **게이트 A (계획 승인)** | 이슈 + 계획 + 명세 + ADR을 사용자가 승인 |
| **Generate** | 승인된 문서대로 구현 |
| **Push+PR / 게이트 B** | feature push → PR(base: dev, `Closes #N`) → **Claude 자체 리뷰·수정** → 머지 직전 멈춤 → **사람 최종 검토·merge** (`docs/workflow/issue-pr-guide.md`) |
| **merge 후** | ① `design.md` 작성/갱신(SSOT, 필수) → ② ongoing 문서를 `changes/`로 **파일명 그대로 이동** → ③ **feature 브랜치 삭제**(로컬+원격) |

> Evaluate 통과 후 design.md 갱신 + ongoing→changes 이동을 빼먹지 않는다.
> 둘 다 해야 "진행 중 큐"와 "진실의 원천"이 최신 상태를 유지한다.

---

## 진행 현황 가시화

별도 대시보드 없이 **`ongoing/` 폴더 자체가 현황판**이다.

```bash
ls docs/dev/ongoing/                          # 진행 중 작업 목록 (한눈에)
grep -rl "담당: 홍길동" docs/dev/ongoing/       # 특정 담당자의 진행 중 작업
```

- 팀/원격으로 확장되면 이 역할을 **GitHub Projects·Jira 같은 칸반**으로 승격할 수 있다 (각 ongoing 문서 = 이슈 카드).

---

## 요약 (한눈에)

1. 새 작업(생성/유지보수) → `docs/dev/ongoing/{작업}.md` 생성 (번호 없음, 담당 표기)
2. 승인 → 개발 → Evaluate 통과 시:
   - 대상 기능 `design.md` 갱신(SSOT)
   - ongoing 문서를 그 기능 `changes/`로 **파일명 그대로 이동**(채번 없음)
3. 진행 중 현황은 `ls docs/dev/ongoing/`로 한눈에
