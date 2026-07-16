package com.coffeeorder.domain.collector.client;

import com.coffeeorder.domain.collector.dto.CollectorTransmitRequest;
import com.coffeeorder.domain.collector.mock.MockCollectorController;
import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorClient {

	private static final int MAX_ATTEMPTS = 3;

	private final RestClient restClient;

	public void send(OrderCompletedEvent event) {
		if (event == null) {
			log.warn("수신한 주문 완료 이벤트가 null이라 데이터 수집 플랫폼 전송을 건너뜀");
			return;
		}

		CollectorTransmitRequest request = CollectorTransmitRequest.from(event);

		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				restClient.post()
						.uri(MockCollectorController.ORDERS_PATH)
						.body(request)
						.retrieve()
						.toBodilessEntity();
				return;
			} catch (Exception e) {
				if (attempt == MAX_ATTEMPTS) {
					log.error("데이터 수집 플랫폼 전송 실패 (orderGroupId={}, {}회 시도 모두 실패)",
							event.orderGroupId(), MAX_ATTEMPTS, e);
				} else {
					log.warn("데이터 수집 플랫폼 전송 재시도 (orderGroupId={}, attempt={})",
							event.orderGroupId(), attempt, e);
				}
			}
		}
	}
}
