# 커피 메뉴 목록 조회 API

대상: menu/list
이슈: [#3](https://github.com/yxejxnn/coffee-order-system/issues/3)

## 배경 / 요구
발제 1번 — 메뉴 id·이름·가격 목록 조회.

## 설계 (HOW)
> 집을 때 작성.
- `GET /api/menus`, menu controller/service/repository/dto.

## 관련 결정·질문
- [`docs/api/menu.md`](../../api/menu.md)

## 태스크
- [ ] (집을 때 세분화)

## 평가(통과) 기준
- 200 + 목록/빈목록 `[]`, 컨트롤러·서비스 테스트(Level 3~4), 실제 HTTP(**Level 6**).
