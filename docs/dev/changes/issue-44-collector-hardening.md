# collector 하드닝

대상: collector/consume
이슈: [#44](https://github.com/yxejxnn/coffee-order-system/issues/44), [#46](https://github.com/yxejxnn/coffee-order-system/issues/46) (같은 파일, 같은 PR로 묶어 처리)
담당: Claude

## 배경 / 요구
- #44: `CollectorClient`의 재시도(최대 3회)에 backoff가 없어, 연결 거부처럼 즉시 실패하는 경우 3회가 사실상 한 순간에 소진됨.
- #46: `CollectorClient`가 `RestClient`를 타입으로만 주입받아, 다른 `RestClient` 빈이 추가되는 순간 `NoUniqueBeanDefinitionException`으로 깨질 수 있음(지금은 `collectorRestClient` 하나뿐이라 우연히 동작).

## 설계
- #44: 재시도를 제거하지 않고 **200ms 고정 backoff**를 추가(`RETRY_BACKOFF_MS`, `sleepBackoff()`). 대기 중 인터럽트되면 인터럽트 상태를 복원하고 재시도를 포기하도록 처리해, "이 메서드는 예외를 던지지 않는다"는 기존 계약을 유지. Javadoc과 `docs/dev/collector/consume/design.md`의 실패 정책 문구도 갱신.
- #46: `restClient` 필드에 `@Qualifier("collectorRestClient")` 추가.

## 자체 리뷰에서 발견·수정한 문제
초안은 "Lombok이 `@RequiredArgsConstructor` 생성자를 만들 때 `@Qualifier`를 필드에서 파라미터로 자동 복사한다"고 가정했으나, `javap -v -p`로 컴파일된 생성자를 직접 까본 결과 파라미터에 `RuntimeVisibleParameterAnnotations`가 전혀 없었다 — 즉 `@Qualifier`가 필드에만 남고 Spring이 실제로 보는 생성자 파라미터엔 전달되지 않아 **#46이 고쳐지지 않은 채로 있었다**(다른 `RestClient` 빈이 추가돼도 여전히 `NoUniqueBeanDefinitionException`). 원인은 이 저장소에 `lombok.config`가 없어 Lombok의 `copyableAnnotations` 목록이 비어 있었기 때문. 프로젝트 루트에 `lombok.config`를 추가해 `lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier`를 등록해 근본적으로 고쳤다(개별 클래스에 수동 생성자를 쓰는 특수 처리 대신, `@RequiredArgsConstructor`를 쓰는 모든 곳에서 앞으로도 `@Qualifier`가 정상 동작하도록 메커니즘 자체를 일반화). 수정 후 `javap`로 생성자 파라미터에 `@Qualifier("collectorRestClient")`가 실제로 붙는 것을 재확인했고, `CollectorEventConsumerTest`(전체 Spring 컨텍스트 + 임베디드 Kafka, `DB_USERNAME/DB_PASSWORD` 등 env 설정 필요)로 실제 빈 주입까지 통과 확인.

## 태스크
- [x] `CollectorClient`에 `@Qualifier("collectorRestClient")` 추가
- [x] `CollectorClient`에 200ms 고정 backoff 추가(`sleepBackoff()`)
- [x] 클래스/메서드 Javadoc 갱신
- [x] `docs/dev/collector/consume/design.md` 실패 정책 문구 갱신
- [x] (자체 리뷰 발견) `lombok.config` 추가로 `@Qualifier`가 실제 생성자 파라미터에 전달되도록 수정

## 평가(통과) 기준
- `CollectorClientTest`(2개) 회귀 없음 — 재시도 테스트는 backoff로 인해 ~0.6초로 느려지지만 여전히 PASS.
- `./gradlew compileJava` PASS.
- `javap -v -p`로 생성자 파라미터의 `@Qualifier` 존재 직접 확인(수정 전 부재 → 수정 후 존재).
- `CollectorEventConsumerTest`(전체 Spring 컨텍스트, DB env 설정 후) PASS — 실제 빈 주입 경로까지 검증.
- 검증 레벨: Level 1(단위) + Level 4(통합, `CollectorEventConsumerTest`) PASS.
