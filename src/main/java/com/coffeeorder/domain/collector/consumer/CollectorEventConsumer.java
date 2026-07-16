package com.coffeeorder.domain.collector.consumer;

import com.coffeeorder.config.KafkaTopics;
import com.coffeeorder.domain.collector.client.CollectorClient;
import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 주문 완료 이벤트를 소비해 데이터 수집 플랫폼 전송을 트리거하는 Kafka 컨슈머(소비자 그룹 (a)).
 *
 * <p>{@code order-completed} 토픽을 {@value #GROUP_ID} 컨슈머 그룹으로 구독한다. 같은 토픽을
 * 소비하는 다른 그룹(예: 인기 메뉴 집계용 {@code ranking-group})과는 Kafka의 pub-sub 팬아웃 덕분에
 * 서로 간섭 없이 독립적으로 메시지를 받는다.
 *
 * <p>이 클래스 자체는 아무 로직도 갖지 않고, 실제 전송·재시도·실패 처리는 전부
 * {@link CollectorClient#send}에 위임한다({@code CollectorClient}가 모든 예외를 내부에서
 * 삼키므로, 이 리스너 메서드는 예외 없이 항상 정상 반환하고 Kafka 오프셋도 정상 커밋된다).
 *
 * @see CollectorClient
 */
@Component
@RequiredArgsConstructor
public class CollectorEventConsumer {

	/**
	 * 이 컨슈머의 Kafka 컨슈머 그룹 ID. 첫 기동 시(커밋된 오프셋 없음) 토픽 처음부터 읽도록
	 * {@code application.yml}의 {@code spring.kafka.consumer.auto-offset-reset: earliest}와
	 * 함께 동작한다.
	 */
	private static final String GROUP_ID = "collector-group";

	private final CollectorClient collectorClient;

	/**
	 * {@code order-completed} 토픽에서 주문 완료 이벤트를 받아 데이터 수집 플랫폼 전송을 위임한다.
	 *
	 * @param event 주문 완료 시 발행된 이벤트(orderGroupId, memberId, menuId, totalPrice)
	 */
	@KafkaListener(topics = KafkaTopics.ORDER_COMPLETED, groupId = GROUP_ID)
	public void consume(OrderCompletedEvent event) {
		collectorClient.send(event);
	}
}
