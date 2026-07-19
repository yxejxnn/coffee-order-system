# collector 하드닝

대상: collector/consume
이슈: [#44](https://github.com/yxejxnn/coffee-order-system/issues/44), [#46](https://github.com/yxejxnn/coffee-order-system/issues/46) (같은 파일, 같은 PR로 묶어 처리)
담당: Claude

## 배경 / 요구
- #44: `CollectorClient`의 재시도(최대 3회)에 backoff가 없어, 연결 거부처럼 즉시 실패하는 경우 3회가 사실상 한 순간에 소진됨.
- #46: `CollectorClient`가 `RestClient`를 타입으로만 주입받아, 다른 `RestClient` 빈이 추가되는 순간 `NoUniqueBeanDefinitionException`으로 깨질 수 있음(지금은 `collectorRestClient` 하나뿐이라 우연히 동작).

## 설계
- #44: 재시도를 제거하지 않고 **200ms 고정 backoff**를 추가(`RETRY_BACKOFF_MS`, `sleepBackoff()`). 대기 중 인터럽트되면 인터럽트 상태를 복원하고 재시도를 포기하도록 처리해, "이 메서드는 예외를 던지지 않는다"는 기존 계약을 유지. Javadoc과 `docs/dev/collector/consume/design.md`의 실패 정책 문구도 갱신.
- #46: `restClient` 필드에 `@Qualifier("collectorRestClient")` 추가. `@RequiredArgsConstructor`(Lombok)가 생성자를 만들 때 `@Qualifier`를 필드에서 생성자 파라미터로 자동 복사하므로 별도 생성자 작성 불필요.

## 관련 결정·질문
- 없음(둘 다 완료조건이 명확한 하드닝, 별도 트레이드오프 논쟁 없음).

## 태스크
- [x] `CollectorClient`에 `@Qualifier("collectorRestClient")` 추가
- [x] `CollectorClient`에 200ms 고정 backoff 추가(`sleepBackoff()`)
- [x] 클래스/메서드 Javadoc 갱신
- [x] `docs/dev/collector/consume/design.md` 실패 정책 문구 갱신

## 평가(통과) 기준
- `CollectorClientTest`(2개) 회귀 없음 — 재시도 테스트는 backoff로 인해 ~0.6초로 느려지지만 여전히 PASS.
- `./gradlew compileJava` PASS.
- 검증 레벨: Level 1(단위, 기존 `CollectorClientTest` 재사용) PASS.
