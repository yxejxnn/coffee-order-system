package com.coffeeorder.domain.order.event;

import com.coffeeorder.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletedEventListener {

	private final KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(OrderCompletedEvent event) {
		kafkaTemplate.send(KafkaTopics.ORDER_COMPLETED, event.orderGroupId(), event)
				.whenComplete((result, ex) -> {
					if (ex != null) {
						log.error("주문 완료 이벤트 발행 실패 (orderGroupId={})", event.orderGroupId(), ex);
					}
				});
	}
}
