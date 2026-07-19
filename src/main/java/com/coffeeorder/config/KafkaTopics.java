package com.coffeeorder.config;

public final class KafkaTopics {

	public static final String ORDER_COMPLETED = "order-completed";

	// KafkaErrorHandlingConfig의 DeadLetterPublishingRecoverer 기본 네이밍 컨벤션(원본 토픽 + "-dlt")과 일치.
	// "."을 쓰지 않는다 — Kafka는 토픽명의 "."/"_" 를 메트릭·로그디렉터리 이름에서 같은 문자로
	// 접어버려서, 대소문자 구분 없는 파일시스템(macOS 기본 APFS)에서 실제로 브로커가 죽는 충돌을
	// 겪었다(로컬에서 직접 재현·확인).
	public static final String ORDER_COMPLETED_DLT = ORDER_COMPLETED + "-dlt";

	private KafkaTopics() {
	}
}
