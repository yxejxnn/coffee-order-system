# menu/list — Design

## 개요
등록된 커피 메뉴 전체를 id·이름·가격으로 목록 조회한다. 메뉴 등록/수정 API는 없음(발제 범위 밖) — `DataSeeder`가 초기 시드로 채운다.

## API / 인터페이스
- `GET /api/menus` → `200 { "code": "SUCCESS", "data": [{ "id", "name", "price" }] }`. 데이터 없으면 `data: []`. 에러 응답 없음(항상 목록 반환). 상세: `docs/api/menu.md`.
- 구현: `domain/menu/controller/MenuController` → `domain/menu/service/MenuService` → `domain/menu/repository/MenuRepository`(커스텀 쿼리 없는 단순 `JpaRepository`) → `domain/menu/dto/MenuResponse`(정적 팩토리 `from(Menu)`).
- 정렬 순서는 별도로 보장하지 않는다(`findAll()` 기본 순서, 명시적 `ORDER BY` 없음) — API 계약에 정렬 요구사항이 없어 의도적으로 생략.

## 데이터 모델
`menus` 테이블(`domain/menu/entity/Menu`)을 그대로 읽기만 한다. 상세: `docs/db/menu.md`.

## 규칙 / 검증
- 조회 전용이라 도메인 규칙·검증 대상 없음.
- `MenuService`는 `@Transactional(readOnly = true)`.
- 컨트롤러/서비스는 생성자 주입에 Lombok `@RequiredArgsConstructor` 사용(`docs/code-convention.md`).
