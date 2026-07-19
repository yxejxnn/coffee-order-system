# ranking API

인기 메뉴 조회. (발제 요구사항 4) — 최근 7일 인기 메뉴 3개, 주문 횟수가 정확해야 함.

## GET /api/menus/popular — 인기 메뉴 목록 조회
- 요청 query: 없음 (상위 3개 고정)
- 응답: `200 OK`
  ```json
  {
    "code": "SUCCESS",
    "data": [
      { "rank": 1, "menuId": 2, "name": "카페라떼", "orderCount": 128 },
      { "rank": 2, "menuId": 1, "name": "아메리카노", "orderCount": 97 },
      { "rank": 3, "menuId": 5, "name": "바닐라라떼", "orderCount": 40 }
    ]
  }
  ```
  | 필드 | 타입 | 설명 |
  |------|------|------|
  | rank | number | 순위(1~3) |
  | menuId | number | 메뉴 ID |
  | name | string | 메뉴 이름 |
  | orderCount | number | 최근 7일 주문 횟수 |

## 규칙
- **(#47) 경로가 `RankingController`(ranking 도메인)에 있지만 `/api/menus/popular`인 이유**: 경로는 리소스(메뉴) 기준, 컨트롤러 소속은 로직 소유(ranking) 기준 — 서로 다른 축이라 의도적으로 분리했다. 상세는 [dev/ranking/popular/design](../dev/ranking/popular/design.md) 참조.
- **최근 7일** = 조회 시점 기준 당일 포함 7개 일자 버킷의 합. → [policy/popular-menu](../policy/popular-menu.md)
- 카운트 원천(SSOT)은 `ORDERS`. 조회는 **Redis ZSET**(일자별 버킷 union)에서 읽는다. Kafka 소비 시 `order_group_id` 멱등 처리로 **정확한 카운트** 보장. → [ADR-003](../adr/ADR-003-인기메뉴-집계전략.md)
- 동점 시 정렬: `orderCount` 내림차순 → 같으면 `menuId` 오름차순.
- 데이터가 3개 미만이면 있는 만큼만 반환.
