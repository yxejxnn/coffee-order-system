# 인기 메뉴 조회 API

대상: ranking/popular
이슈: [#8](https://github.com/yxejxnn/coffee-order-system/issues/8)

## 배경 / 요구
발제 4번 — 최근 7일 인기 메뉴 3개 조회.

## 설계 (HOW)
> 집을 때 작성.
- `GET /api/menus/popular`: 최근 7일 일자 키 union 상위 3(횟수 desc → menuId asc), 메뉴 이름/카운트 조립.

## 관련 결정·질문
- [`docs/api/ranking.md`](../../api/ranking.md) · [`docs/policy/popular-menu.md`](../../policy/popular-menu.md) · [`ADR-003`](../../adr/ADR-003-인기메뉴-집계전략.md)

## 태스크
- [ ] (집을 때 세분화)

## 평가(통과) 기준
- 7일 경계·union·정렬·3개 미만 케이스(Level 3~4), 실제 HTTP(**Level 6**).
