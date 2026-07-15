# 커피 메뉴 목록 조회 API

대상: menu/list
이슈: [#3](https://github.com/yxejxnn/coffee-order-system/issues/3)

## 배경 / 요구
발제 1번 — 메뉴 id·이름·가격 목록 조회.

## 설계 (HOW)
- `Menu` 엔티티·`MenuRepository`(#2에서 이미 생성, 커스텀 쿼리 없는 단순 `JpaRepository`)는 그대로 재사용.
- **`domain/menu/dto/MenuResponse.java`** — id/name/price, `from(Menu)` 정적 팩토리 (`docs/code-convention.md` DTO 규칙).
- **`domain/menu/service/MenuService.java`** — `@Service` + `@Transactional(readOnly = true)`, `MenuRepository` 생성자 주입(명시적 생성자, `DataSeeder`와 동일 스타일). `getMenus()`가 `findAll()` → `MenuResponse::from` 매핑. 빈 테이블이면 빈 리스트 자동 반환.
- **`domain/menu/controller/MenuController.java`** — `@RestController`, `@RequestMapping("/api/menus")`, `GET` → `ResponseEntity<ApiResponse<List<MenuResponse>>>`.
- 테스트는 기존 `GlobalExceptionHandlerTest`/`DataSeederTest` 스타일(Spring 컨텍스트 없이 직접 인스턴스화 + Mockito)을 따름 — 컨트롤러가 이번이 처음이라 `@WebMvcTest` 슬라이스를 새로 들이지 않고 기존 패턴 유지.

## 관련 결정·질문
- [`docs/api/menu.md`](../../api/menu.md)
- Level 판단: 로드맵의 "Level 3~4"는 락/외부인프라용 일반 라벨인데 이 기능은 단순 조회라 Level 3(락·동시성)·Level 4(메시징·캐시) 해당 없음. 실제로는 단위 테스트(Service/Controller) + Level 6(실제 HTTP)만 밟는다.

## 태스크
- [ ] MenuResponse DTO
- [ ] MenuService (+ 단위 테스트)
- [ ] MenuController (+ 단위 테스트)
- [ ] Level 6 실제 HTTP 확인 + 로그 기록

## 평가(통과) 기준
- 200 + 목록/빈목록 `[]`, 컨트롤러·서비스 단위 테스트, 실제 HTTP(**Level 6**).
