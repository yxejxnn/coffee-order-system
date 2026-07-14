# 도메인 엔티티 + 스키마 + 시드

대상: domain/entity
이슈: [#2](https://github.com/yxejxnn/coffee-order-system/issues/2)

## 배경 / 요구
5개 엔티티와 초기 데이터를 잡아 이후 기능 이슈의 기반을 만든다.

## 설계 (HOW)
> 집을 때 작성.
- `Member`·`Point`·`PointHistory`·`Menu`·`Orders` JPA 엔티티(생성자 직접 작성, Lombok 생성자 애노테이션 금지), 회원·메뉴 시드.

## 관련 결정·질문
- `docs/db/erd.md` + 각 테이블 md (스키마 원천)
- [`ADR-004`](../../adr/ADR-004-데이터모델-포인트분리.md) (POINT 1:1 분리, order_group_id, no ORDER_ITEM)

## 태스크
- [ ] (집을 때 세분화)

## 평가(통과) 기준
- 엔티티 ↔ `docs/db/` 명세 일치, 스키마 생성/기동 — **Level 5**.
- 1:1(Point)·FK 매핑 검증 테스트.
