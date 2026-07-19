package com.coffeeorder.config;

import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * {@code ranking-group}({@link com.coffeeorder.domain.ranking.consumer.RankingEventConsumer}) 전용
 * 재시도·DLT 정책.
 *
 * <p>이 정책은 인기 메뉴 집계의 정확성 요구(#41) 때문에 필요한 것으로, ranking 도메인 고유의 결정이다.
 * 그래서 {@link org.springframework.kafka.listener.CommonErrorHandler}를 컨텍스트에 노출되는 공용
 * {@code @Bean}으로 만들지 않고, 이 전용 {@link ConcurrentKafkaListenerContainerFactory}(빈 이름
 * {@code rankingKafkaListenerContainerFactory}) 안에서만 구성한다 — 그래야 Spring Boot의 기본
 * 자동구성 팩토리(다른 {@code @KafkaListener}가 쓰는 쪽, 지금은 {@code collector-group})가 이 정책을
 * 암묵적으로 상속받지 않는다. {@code collector-group}은 {@code CollectorClient}가 예외를 삼켜 지금은
 * 이 정책이 있어도 없어도 차이가 없지만, 향후 세 번째 {@code @KafkaListener}가 추가돼도 이 리스너가
 * {@code containerFactory}를 명시하지 않는 한 이 정책의 영향을 받지 않는다(자체 리뷰에서 발견 — 전역
 * 빈으로 뒀을 때의 altitude 문제).
 *
 * @see com.coffeeorder.domain.ranking.consumer.RankingEventConsumer
 */
@Configuration
public class KafkaErrorHandlingConfig {

	@Bean
	public ConcurrentKafkaListenerContainerFactory<Object, Object> rankingKafkaListenerContainerFactory(
			ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
			ConsumerFactory<Object, Object> kafkaConsumerFactory,
			KafkaOperations<Object, Object> kafkaOperations) {
		ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
		configurer.configure(factory, kafkaConsumerFactory);
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations);
		factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2)));
		return factory;
	}
}
