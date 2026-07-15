# 데이터 수집 플랫폼 전송 컨슈머

대상: collector/consume
이슈: [#6](https://github.com/yxejxnn/coffee-order-system/issues/6)

## 배경 / 요구
발제 3번 — 주문내역을 데이터 수집 플랫폼으로 실시간 전송(Mock).

## 설계 (HOW)
> 집을 때 작성.
- 이벤트 소비 그룹 (a): Mock 데이터 수집 API로 `memberId, menuId, 결제금액` 전송. 전송 실패 정책.

## 관련 결정·질문
- [`docs/api/order.md`](../../api/order.md) · [`ADR-002`](../../adr/ADR-002-주문이벤트-비동기전달.md)
- Open: 전송 실패 시 재시도/보관 정책 → `docs/open-questions.md`

## 태스크
- [ ] (집을 때 세분화)

## 평가(통과) 기준
- 이벤트 → Mock 전송 호출 확인(Mock/WireMock), 실패 처리 검증 — **Level 4~5**.
