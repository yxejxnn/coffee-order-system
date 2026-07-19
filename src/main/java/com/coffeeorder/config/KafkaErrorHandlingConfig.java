package com.coffeeorder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 모든 {@code @KafkaListener}에 공통으로 적용되는 재시도·DLT 정책.
 *
 * <p>Spring Boot가 자동구성하는 {@code ConcurrentKafkaListenerContainerFactory}는 컨텍스트에 있는
 * 단일 {@link CommonErrorHandler} 빈을 자동으로 집어써서, 별도 팩토리 커스터마이징 없이
 * {@code ranking-group}·{@code collector-group} 리스너 모두에 적용된다.
 *
 * <p>{@code collector-group}({@link com.coffeeorder.domain.collector.consumer.CollectorEventConsumer})은
 * {@code CollectorClient}가 예외를 전부 내부에서 삼켜 리스너가 절대 던지지 않으므로, 이 설정은
 * 실질적으로 {@code ranking-group}({@link com.coffeeorder.domain.ranking.consumer.RankingEventConsumer})의
 * 실패(예: Redis 장애)에만 적용된다 — 재시도를 몇 번 안에서 끝내고, 그래도 실패하면 이벤트를 조용히
 * 버리는 대신 {@link KafkaTopics#ORDER_COMPLETED_DLT}로 보존한다(#41).
 */
@Configuration
public class KafkaErrorHandlingConfig {

	@Bean
	public CommonErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations);
		return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2));
	}
}
