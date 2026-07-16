package com.coffeeorder.domain.collector.consumer;

import com.coffeeorder.config.KafkaTopics;
import com.coffeeorder.domain.collector.client.CollectorClient;
import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollectorEventConsumer {

	private final CollectorClient collectorClient;

	@KafkaListener(topics = KafkaTopics.ORDER_COMPLETED, groupId = "collector-group")
	public void consume(OrderCompletedEvent event) {
		collectorClient.send(event);
	}
}
