package com.coffeeorder.domain.ranking.consumer;

import com.coffeeorder.config.KafkaTopics;
import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import com.coffeeorder.domain.ranking.service.RankingAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 주문 완료 이벤트를 소비해 인기 메뉴 집계(Redis ZSET)를 트리거하는 Kafka 컨슈머(소비자 그룹 (b)).
 *
 * <p>{@code order-completed} 토픽을 {@value #GROUP_ID} 컨슈머 그룹으로 구독한다. 데이터 수집
 * 플랫폼 전송용 {@code collector-group}과는 Kafka pub-sub 팬아웃으로 서로 독립적으로 메시지를 받는다.
 *
 * @see RankingAggregationService
 */
@Component
@RequiredArgsConstructor
public class RankingEventConsumer {

	private static final String GROUP_ID = "ranking-group";

	private final RankingAggregationService rankingAggregationService;

	@KafkaListener(topics = KafkaTopics.ORDER_COMPLETED, groupId = GROUP_ID,
			containerFactory = "rankingKafkaListenerContainerFactory")
	public void consume(OrderCompletedEvent event) {
		rankingAggregationService.aggregate(event);
	}
}
