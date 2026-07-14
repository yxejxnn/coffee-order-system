# 커밋 컨벤션 (commit-convention)

커밋 메시지를 작성할 때 이 규칙을 따른다.
표준 **Conventional Commits** 기반이며, 세부는 이 프로젝트에 맞게 조정한다.

> ⚠️ **커밋은 기능 구현 단위로** 한다 (하나의 커밋 = 하나의 작업). push는 **feature 브랜치만**, `main`·`dev`로의 직접 커밋/push는 훅이 차단한다 (`docs/branch-guide.md`).

## 형식

```
<type>(<scope>): <subject>

<body (선택)>

<footer (선택)>
```

- **type**: 변경 성격 (아래 표)
- **scope**: 영향 범위 = 보통 **개념/기능** (예: `schedule`, `schedule/create`, `refund`)
- **subject**: 한 줄 요약 (한글 가능, 명령형, 마침표 없음, 50자 내)

## type 종류

| type | 용도 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서만 변경 (design.md, changes/, 가이드 등) |
| `refactor` | 동작 변화 없는 구조 개선 |
| `test` | 테스트 추가/수정 |
| `chore` | 빌드·설정·의존성 등 잡무 |
| `style` | 포맷·세미콜론 등 (동작 무관) |

## 예시

```
feat(schedule/create): 일정 생성 API 추가

- POST /api/schedules
- 제목·시작/종료 시각 검증 포함
- 관련 개발문서: docs/dev/schedule/create/changes/001-create.md
```

```
docs(schedule/create): 001-create 완료 처리 및 design.md 갱신
fix(schedule): 종료시각이 시작시각보다 빠를 때 검증 누락 수정
```

## 워크플로우 연결

- **Generate/Evaluate 완료 후** 커밋할 때, 본문에 관련 이슈(`refs #N`)나 `changes/00X` 문서를 링크하면 추적성이 좋아진다.
- 하나의 커밋 = **하나의 기능 구현(작업/change) 단위**. 여러 작업을 한 커밋에 뭉치지 않는다.
- 커밋 후 **feature 브랜치를 push하고 PR을 올린다** (base: `dev`). `main`·`dev` 직접 커밋/push는 금지(훅). 흐름 세부: `docs/workflow/issue-pr-guide.md`.
