---
name: 기능 작업
about: 하나의 구현 기능 또는 API
title: "[feat] {개념}/{기능}: "
labels: feature
assignees: ""
---

<!-- 계획 단계에서 전체를 잘게 쪼갠 이슈 하나. WHAT(목표·범위·계약·완료조건)은 상세히, HOW(구현)는 비워 둔다. -->

## 목표

## 범위

## 제외 범위

## API 계약

<!-- 엔드포인트·요청/응답. 상세 명세는 docs/api/ 참조. -->

## 도메인 규칙

<!-- 비즈니스 규칙·검증. 관련 정책은 docs/policy/ 참조. -->

## 완료 기준

- [ ] `docs/workflow/issue-completion-checklist.md`의 해당 항목을 확인했습니다.
- [ ] 요구사항이 구현되었습니다.
- [ ] `docs/logs/`에 이번 시도(evidence)를 기록했습니다. (`docs/logs-guide.md`)

## 실제 실행 검증 결정

<!-- Level 5(로컬 기동)·Level 6(실제 HTTP) 필요 여부. 기능 Issue의 보수적 기본값은 YES. PR이 이 값을 그대로 복사합니다. -->
Level 5 required: YES
Level 5 reason: 실제 애플리케이션 기동 검증이 불필요하다면 NO로 바꾸고 근거를 작성합니다.
Level 6 required: YES
Level 6 reason: 실제 HTTP 요청 검증이 불필요하다면 NO로 바꾸고 근거를 작성합니다.

## 검증 계획

<!-- 레벨 정의는 docs/workflow/evaluate-guide.md 참조. -->

| Level | 예상 확인 |
| --- | --- |
| Level 1 (빌드+단위+회귀) |  |
| Level 2 (Controller/API 계약) |  |
| Level 3 (DB·트랜잭션·동시성) |  |
| Level 4 (외부 인프라, 해당 시) |  |
| Level 5 (로컬 기동) |  |
| Level 6 (실제 HTTP) |  |
| Level 7 (부하, 선택) |  |
