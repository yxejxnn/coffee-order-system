# 코드 컨벤션 (code-convention)

코드를 작성·수정할 때 이 규칙을 따른다. (Spring Boot / Java 기준)
세부는 이 프로젝트에 맞게 조정한다.

## 패키지 · 계층 구조

- 루트 패키지: `com.coffeeorder`
- **도메인(기능) 우선 패키징**: `com.coffeeorder.domain.{도메인}.{계층}` (예: `com.coffeeorder.domain.member.entity.Member`, `com.coffeeorder.domain.point.repository.PointRepository`). 계층(`controller`/`service`/`repository`/`entity`/`dto`)이 아니라 **도메인이 먼저** 온다.
  - 현재 도메인: `member`·`point`(+`PointHistory`, 잔액과 감사이력을 한 도메인으로 묶음)·`menu`·`order`(엔티티 `Order`) — `docs/db/*.md` 테이블 그룹과 대응.
  - 여러 도메인을 넘나드는 것(설정·전역 예외 등)은 도메인 패키지 밖(`com.coffeeorder.config`, `com.coffeeorder.common`)에 둔다.
- 계층 분리(각 도메인 패키지 내부): `controller` / `service` / `repository` / `entity` / `dto`
- 각 계층의 책임을 지킨다:
  - **controller**: 요청/응답만. 비즈니스 로직 금지 → service로 위임.
  - **service**: 비즈니스 로직, 트랜잭션 경계.
  - **repository**: 데이터 접근 (`JpaRepository`).
  - **entity**: JPA 엔티티. 컨트롤러 응답으로 **직접 노출 금지**. 필드를 직접 선언한다.
    - **no-arg 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`**(Lombok)로 생성한다 — JPA 프록시 생성용, 직접 호출하지 않으므로 protected로 감춘다.
    - **필드(비즈니스) 생성자는 직접 작성**한다 (Lombok `@AllArgsConstructor`/`@RequiredArgsConstructor` 사용 안 함) — 파라미터 순서·의미를 명시적으로 통제하기 위함.
    - **`@Table(name = "...")`은 복수형**으로 쓴다 (`Member`→`members`, `Point`→`points`, `PointHistory`→`point_histories`, `Menu`→`menus`, `Order`→`orders`). 엔티티/도메인 이름은 단수(Java 클래스명과 일치), 테이블명만 복수형.
    - **다른 엔티티 참조 방식(객체 연관관계 vs FK id)은 아래 기준으로 그때그때 판단**한다 — 한쪽으로 무조건 통일하지 않는다.
      - **`@ManyToOne`/`@OneToOne` 객체 참조가 맞는 경우**: 실제로 연관 엔티티의 필드를 함께 조회·응답해야 한다(fetch join으로 한 번에 가져오는 게 이득) / 같은 도메인 내부 참조라 도메인 간 결합이 생기지 않는다 / DB가 FK 제약(참조 무결성)을 자동으로 걸어주길 원한다 / cascade(영속·삭제 전파)가 필요하다.
      - **FK id(`Long`)만 보유가 맞는 경우**: 그 관계에서 필요한 건 id뿐이다(연관 필드가 필요해지면 그때 별도 조회하면 됨) / 서로 다른 도메인 패키지 간 참조라 엔티티 클래스 간 컴파일 의존을 피하고 싶다 / LAZY 로딩·N+1 리스크를 원천 차단하고 싶다 / 그 엔티티를 다른 도메인과 독립적으로 테스트·영속하고 싶다.
      - **판단 순서**: 먼저 그 기능의 API/화면 스펙(`docs/api/`)이 연관 엔티티의 필드까지 실제로 필요로 하는지 확인한다. 애매하면 **FK id만 보유**로 시작한다(결합이 약해 나중에 fetch join 조회로 넓히기 쉽고, 반대로 객체 참조를 나중에 id로 좁히는 건 되돌리기 더 번거롭다).
      - **적용 예**: `Order.memberId`/`Order.menuId`, `Point.memberId`, `PointHistory.memberId` — 계획된 API 응답(`docs/api/order.md` 등)이 raw id만 필요로 하고, 모두 서로 다른 도메인 간 참조라 FK id만 보유를 선택했다. → [ADR-005](adr/ADR-005-엔티티간-FK-ID-참조.md)
      - FK id만 보유할 경우 DB FK 제약은 자동 생성되지 않으므로, 참조 무결성은 서비스 레이어의 존재 검증(예: `MEMBER_NOT_FOUND`)에 의존하게 됨을 감안한다.
    - **enum 필드는 `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`를 함께 쓴다.** Hibernate 7이 MySQL 등 네이티브 enum을 지원하는 dialect에서 `@Enumerated(STRING)`만 있으면 기본적으로 `enum(...)` 컬럼을 생성하므로, 스키마 문서(`docs/db/`)가 `VARCHAR`를 명시한 경우 이 조합이 필요하다 (`PointHistory.type` 참고).
  - **dto**: 요청/응답 전용 객체.

## 의존성 주입

- **생성자 주입**을 사용한다. 필드 `@Autowired` **금지**.
- 가능하면 필드는 `final`로 둔다.
- **controller/service 등 일반 컴포넌트는 생성자를 직접 쓰지 않고 Lombok `@RequiredArgsConstructor`로 생성**한다 (엔티티는 예외 — 위 "entity" 항목처럼 필드 생성자를 직접 작성).

## 트랜잭션

- 서비스 클래스에 `@Transactional(readOnly = true)`를 기본으로 두고,
  쓰기 메서드에만 `@Transactional`을 명시한다.

## 웹 · 검증

- REST 컨트롤러는 `@RestController`, 매핑은 `/api/...`.
- 요청 DTO에 Bean Validation(`@Valid` + 제약 애노테이션)을 적용한다.
- 예외는 `@RestControllerAdvice`로 일관 처리한다.
- 적절한 HTTP 상태코드 사용 (생성 `201`, 조회 `200`, 검증 실패 `400` 등).

## 응답 형식 · DTO

### 컨트롤러 응답
- **모든 컨트롤러 메서드는 예외 없이 `ResponseEntity`로 반환**한다 — 반환 타입은 **`ResponseEntity<ApiResponse<응답DTO>>`**로 통일한다. DTO를 그대로 반환하거나 `void`로 두지 않는다.
  - **응답 데이터가 없으면** 데이터 자리를 `Void`로 두어 **`ResponseEntity<ApiResponse<Void>>`**로 반환한다. (`ApiResponse` 래퍼는 항상 유지 — 데이터만 `Void`)
  - `ApiResponse<T>`는 공통 응답 래퍼(상태·데이터·메시지를 감싸는 표준 봉투). 프로젝트에 없으면 만들어 둔다.
  - HTTP 상태코드는 `ResponseEntity`로, 응답 바디의 데이터는 `ApiResponse<T>`로 감싼다.

### 응답 DTO
- 클래스·필드는 **`final`**로 두어 불변으로 만든다.
- 생성자는 직접 쓰지 않고 **`@RequiredArgsConstructor`**(Lombok)로 생성한다. (JSON 직렬화용 getter는 `@Getter`)
- 엔티티 → DTO 변환은 **정적 팩토리 메서드 `from`**으로 묶는다. 컨트롤러/서비스가 `new`로 직접 조립하지 않는다.

```java
@Getter
@RequiredArgsConstructor
public final class ScheduleResponse {
	private final Long id;
	private final String title;

	public static ScheduleResponse from(Schedule schedule) {
		return new ScheduleResponse(schedule.getId(), schedule.getTitle());
	}
}
```

```java
// 컨트롤러
public ResponseEntity<ApiResponse<ScheduleResponse>> create(@Valid @RequestBody ScheduleCreateRequest request) {
	Schedule saved = scheduleService.create(request);
	return ResponseEntity.status(HttpStatus.CREATED)
		.body(ApiResponse.ok(ScheduleResponse.from(saved)));
}
```

## 스타일

- 들여쓰기: **탭** (기존 생성 코드와 일관 — 프로젝트 규칙에 맞게 조정 가능).
- 네이밍: 클래스 `PascalCase`, 메서드/필드 `camelCase`, 상수 `UPPER_SNAKE_CASE`.
- 매직 넘버/문자열 지양 → 상수화.

## 현재 상태 메모

<!-- ⚠️ 프로젝트 상황에 맞게 갱신하는 섹션 (Lombok/린터 도입 여부 등). -->
- **Lombok 도입** — 응답 DTO의 `@Getter`·`@RequiredArgsConstructor`, controller/service의 생성자 주입에 `@RequiredArgsConstructor`로 사용한다. **엔티티만 예외**로 `@Getter`+`@NoArgsConstructor(access = AccessLevel.PROTECTED)`만 Lombok에 맡기고, 필드를 받는 생성자는 직접 작성한다. (실제 프로젝트는 `build.gradle`에 lombok 의존성 필요)
- 정적 분석(Checkstyle 등) 린터는 아직 미설정 — 도입되면 위 규칙 일부가 자동 강제된다.
