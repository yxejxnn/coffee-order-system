# 프로젝트 뼈대 + 인프라(Docker Compose)

대상: setup/foundation
이슈: [#1](https://github.com/yxejxnn/coffee-order-system/issues/1)

## 배경 / 요구
빌드·기동되는 최소 프로젝트와 로컬 인프라를 세운다. 이후 모든 이슈의 토대.

## 설계 (HOW)
> 이 이슈를 집을 때 작성한다 (altitude 경계: Plan은 WHAT만, 구현 세부는 Generate에서).
- build.gradle(SB 4.1.0, Java 17 소스 / JDK 21 toolchain), 패키지 계층(`com.coffeeorder`), `ApiResponse<T>`, `@RestControllerAdvice` + 에러코드 enum, docker-compose(MySQL·Redis·Kafka KRaft), application.yml(크리덴셜 fail-fast) + .env.

## 관련 결정·질문
- `docs/code-convention.md` (계층·응답 래퍼·DI 규약)
- 크리덴셜 fail-fast 규약(트래킹되는 application.yml에 기본값 없음)

## 태스크
- [ ] (집을 때 세분화)

## 평가(통과) 기준
- `./gradlew build` 성공, `docker compose up` 후 앱이 3인프라에 연결 기동 — **Level 5(로컬 기동)**.
- `ApiResponse`/전역 예외 핸들러 단위 테스트.
