# 코드 컨벤션 (code-convention)

코드를 작성·수정할 때 이 규칙을 따른다. (Spring Boot / Java 기준)
세부는 이 프로젝트에 맞게 조정한다.

## 패키지 · 계층 구조

- 루트 패키지: `com.coffeeorder`
- 계층 분리: `controller` / `service` / `repository` / `entity` / `dto`
- 각 계층의 책임을 지킨다:
  - **controller**: 요청/응답만. 비즈니스 로직 금지 → service로 위임.
  - **service**: 비즈니스 로직, 트랜잭션 경계.
  - **repository**: 데이터 접근 (`JpaRepository`).
  - **entity**: JPA 엔티티. 컨트롤러 응답으로 **직접 노출 금지**. 필드를 직접 선언하고 **생성자를 직접 작성**한다 (Lombok 생성자 애노테이션 사용 안 함).
  - **dto**: 요청/응답 전용 객체.

## 의존성 주입

- **생성자 주입**을 사용한다. 필드 `@Autowired` **금지**.
- 가능하면 필드는 `final`로 둔다.

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
		.body(ApiResponse.success(ScheduleResponse.from(saved)));
}
```

## 스타일

- 들여쓰기: **탭** (기존 생성 코드와 일관 — 프로젝트 규칙에 맞게 조정 가능).
- 네이밍: 클래스 `PascalCase`, 메서드/필드 `camelCase`, 상수 `UPPER_SNAKE_CASE`.
- 매직 넘버/문자열 지양 → 상수화.

## 현재 상태 메모

<!-- ⚠️ 프로젝트 상황에 맞게 갱신하는 섹션 (Lombok/린터 도입 여부 등). -->
- **Lombok 도입** — 응답 DTO의 `@Getter`·`@RequiredArgsConstructor` 등에 사용한다. **엔티티에는 생성자 애노테이션을 쓰지 않고 필드·생성자를 직접 작성**한다. (실제 프로젝트는 `build.gradle`에 lombok 의존성 필요)
- 정적 분석(Checkstyle 등) 린터는 아직 미설정 — 도입되면 위 규칙 일부가 자동 강제된다.
