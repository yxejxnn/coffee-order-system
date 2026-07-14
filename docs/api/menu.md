# menu API

커피 메뉴 조회. (발제 요구사항 1)

## GET /api/menus — 커피 메뉴 목록 조회
- 요청: 없음
- 응답: `200 OK`
  ```json
  {
    "code": "SUCCESS",
    "data": [
      { "id": 1, "name": "아메리카노", "price": 4000 },
      { "id": 2, "name": "카페라떼", "price": 4500 }
    ]
  }
  ```
  | 필드 | 타입 | 설명 |
  |------|------|------|
  | id | number | 메뉴 ID |
  | name | string | 메뉴 이름 |
  | price | number | 가격(원) |
- 에러: 없음(항상 목록 반환, 비어 있으면 `[]`)

> 응답은 공통 래퍼 `ApiResponse<T>`로 감싼다(`docs/code-convention.md`). 아래 명세의 `data`가 그 payload.
