# 문제 해결 전략 README (발제 0번)

대상: docs/readme
이슈: [#10](https://github.com/yxejxnn/coffee-order-system/issues/10)

## 배경 / 요구
발제 0번(필수) — README에 **설계 내용 · 설계 의도 · 선택한 문제해결 전략과 분석 · 기술 선택 이유 · 실행 방법**을 담는다. 구현이 설계를 실제로 검증한 뒤 최종화해야 정확하므로 마지막 이슈로 미뤄뒀다.

## 설계 (HOW)

**루트 `README.md`를 제출용으로 전면 교체한다.** 기존 루트 README는 "재활용 하네스 틀" 소개 문서였는데, 평가자가 저장소를 열었을 때 가장 먼저 보는 문서가 커피 주문 시스템 설명이어야 한다(사용자 결정). 하네스 소개 문서는 별도 보존 없이 삭제 — 하네스의 실제 규칙은 `AGENTS.md`·`CLAUDE.md`·`docs/workflow/`에 그대로 남아 있어 내용 손실이 아니다.

**분량은 "중간"** — 발제 0번 항목을 README만 읽어도 이해되게 쓰되, 근거 상세(대안 비교표·재검토 조건)는 ADR/db/api 문서로 링크한다. 이유: ADR을 README에 통째로 옮기면 스크롤 부담이 크고, 반대로 링크만 나열하면 평가자가 여러 번 타야 설계 논리가 보인다.

**구성**:
- 요구사항 대응표 — 발제 1~4 + 도전 요구를 엔드포인트·명세 문서와 1:1 매핑
- 실행 방법 — compose 기동 → 환경변수(`.env`가 Spring 앱에 안 먹는 함정 명시) → `bootRun` → curl 4종 → 테스트
- 기술 선택 이유 — 비관적 락 / Kafka / Redis ZSET / Kafka 단일 브로커 4개를 "왜 이걸 골랐나" 관점으로
- 설계 내용 — ERD 요약 + 설계 의도 3가지(POINT 분리, ORDER_ITEM 없음, FK id 참조) + 주문 흐름 다이어그램
- 문제 해결 전략 — 동시성 / 실시간 전송 / 정확한 집계 / 다중 인스턴스 검증. 각 항목마다 **제외한 대안과 그 이유 + 치른 대가**를 같이 적는다(발제 채점 핵심이 "정답"이 아니라 "설계 논증"이므로)
- 의도적으로 제외한 것 — Outbox·재구축 쿼리·Flyway·CI·취소/환불 + 알려진 잔여 위험(동시 같은 Idempotency-Key)
- 문서 맵 — `docs/` 하위 인덱스

## 관련 결정·질문
- [`ADR-001`](../../adr/ADR-001-포인트-동시성제어.md) · [`ADR-002`](../../adr/ADR-002-주문이벤트-비동기전달.md) · [`ADR-003`](../../adr/ADR-003-인기메뉴-집계전략.md) · [`ADR-004`](../../adr/ADR-004-데이터모델-포인트분리.md) · [`ADR-005`](../../adr/ADR-005-엔티티간-FK-ID-참조.md)
- [`docs/open-questions.md`](../../open-questions.md) — 미해결 항목(주문 취소/환불 범위)은 README "제외한 것"에서 그대로 노출
- 하네스 README 삭제에 따라 `AGENTS.md` 6행의 "새 프로젝트 적용법은 `README.md`" 안내를 실제 README 내용에 맞게 수정

## 수정 파일
- `README.md` (전면 교체)
- `AGENTS.md` (README 성격 변경에 따른 한 줄 수정)
- `docs/dev/ongoing/issue-10-docs-readme.md` → 이 문서로 이동

## 태스크
- [x] `docs/db`·`docs/api`·`docs/adr`·`docs/policy` 전수 확인 후 README 작성
- [x] 실행 방법을 실제 `docker-compose.yml`·`application.yml`·`.env.example`·`DataSeeder`와 대조
- [x] README 내 모든 상대 링크 존재 확인
- [x] `AGENTS.md` 안내 문구 정정

## 평가(통과) 기준
- 발제 0번 4개 항목(설계 내용·설계 의도·문제해결 전략과 분석·기술 선택 이유) + 실행 방법 모두 포함.
- 문서 전용 변경 — 코드 로직 변경 없음. `docs/logs/`는 남기지 않는다(구현 시도가 없어 기록할 evidence가 없음. 문서 전용이었던 #36 배치와 같은 처리).
