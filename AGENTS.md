# AGENTS.md

이 저장소에서 코딩 에이전트(Claude Code, Codex 등)가 따르는 **공통 작업 방식**이다.
**세부 규칙은 각 문서(SSOT)에 있고, 이 파일은 흐름과 "언제 어디를 볼지"만 가리킨다.** 규칙이 충돌하면 원천 문서가 이긴다.

> 재활용 하네스 틀이다. 새 프로젝트 적용법은 `README.md`, Claude Code 특화는 `CLAUDE.md`.

## 실행 흐름

개발은 **이슈 단위로 순차** 진행한다. 게이트가 2개다(계획 승인 · PR 검토).

```
Clarify → Plan(이슈 쪼개기) →[게이트 A: 승인]→ Generate → Evaluate
        → Push+PR → 자체 리뷰·수정 →[게이트 B: 머지 직전 사람 검토·merge]→ 다음 이슈
```

- **Clarify**: 요구·정책이 애매하면 여기서 멈추고 질문한다(추측 금지).
- **Plan**: 코드 전에 전체를 잘게 쪼개 GitHub Issue + 계획 문서를 만든다. 승인 전엔 코드 생성 금지.
- **Generate/Evaluate**: 승인된 계획대로만 구현하고, 테스트+규칙 준수로 판정한다.
- **Push+PR**: feature 브랜치에서 커밋·push 후 PR을 올리고, **Claude가 자체 리뷰·수정**을 통과까지 돌린다. 그 뒤 **머지 직전에 멈춰** 사람의 최종 검토를 기다린다. **merge는 사람만**, merge 후 feature 브랜치는 삭제.
- 게이트 2개는 **기본 모드** 기준. 단계·모드·루프·이슈/PR 세부는 라우터([`docs/README.md`](docs/README.md))가 가리키는 가이드를 본다.

## 프로젝트 개요

<!-- ⚠️ 새 프로젝트에 적용하면 먼저 채운다. 유일한 도메인 서술 지점. 확정 전이면 Plan에서 질문(추측 금지). -->

- 이름: `coffee-order-system` (`com.coffeeorder`)
- 도메인 / 목적: 다수 서버·다수 인스턴스 환경에서도 정합성을 지키는 **커피 주문 시스템**(K사 서버 개발 사전과제). 메뉴 조회 · 포인트 충전 · 주문/결제(+데이터 수집 플랫폼 전송) · 최근 7일 인기 메뉴 조회.
- 스택: Java 17(소스 레벨) / JDK 21(런타임) · Spring Boot 4.1.0 · Gradle(Groovy) · MySQL · Redis · Kafka(단일 브로커, KRaft, `spring-kafka` 4.x) · Docker Compose.
- 채점 핵심: 정답이 아니라 **동시성·데이터 일관성·확장성 선택의 근거**를 설득하는 것. 설계 근거는 `docs/adr/`가 원천이다.

## 명령어

wrapper(`./gradlew`) 사용, 전역 gradle 금지. (Maven이면 교체)

```bash
./gradlew build   # 빌드+테스트   ./gradlew test    # 테스트만
./gradlew compileJava  # 컴파일 검증   ./gradlew bootRun  # 앱 실행
```

> ⚠️ **`./gradlew test`/`build`는 실제 MySQL이 필요하다** (엔티티/리포지토리 계층부터 `@DataJpaTest`가 임베디드 DB 없이 실제 datasource를 씀). 먼저 `docker compose up -d mysql` 후 `DB_USERNAME`/`DB_PASSWORD`(`.env`의 `MYSQL_ROOT_PASSWORD`)를 셸 환경변수로 export하고 실행한다 — `.env`는 docker compose만 읽으므로 Gradle 프로세스엔 별도로 넘겨야 한다.

## 컨텍스트 라우터

무슨 일을 하든 **먼저 [`docs/README.md`](docs/README.md)에서 지금 작업에 맞는 문서 3~5개**를 찾아 연다. `docs/` 전체를 훑지 않는다 (컨텍스트 폭주가 "막힘"의 주원인).

## 규칙

- **커밋은 기능 구현 단위.** push는 **feature 브랜치만**, `main`·`dev`는 커밋/push 차단(훅 강제).
- **PR merge는 사람만.** Claude는 자체 리뷰·수정을 통과시킨 뒤 **머지 직전에 멈춰** 사람의 최종 검토를 기다린다. merge 후 feature 브랜치는 삭제.
- 되돌리기 어려운 변경(DB·실행 설정)은 진행 전 사용자에게 확인한다.
- 평가 결과를 사실대로 보고한다. 통과하지 않았으면 "통과"라고 말하지 않는다.
- **애매하면 추측하지 말고 질문한다.**
