package com.coffeeorder.domain.collector.client;

import com.coffeeorder.domain.collector.dto.CollectorTransmitRequest;
import com.coffeeorder.domain.collector.mock.MockCollectorController;
import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 데이터 수집 플랫폼(Mock)으로 주문 완료 이벤트를 HTTP로 전송하는 클라이언트.
 *
 * <p>{@link com.coffeeorder.domain.collector.consumer.CollectorEventConsumer}가 Kafka에서 받은
 * {@link OrderCompletedEvent}를 그대로 넘겨받아, {@link CollectorTransmitRequest}로 변환한 뒤
 * {@link MockCollectorController#ORDERS_PATH}로 {@code POST} 요청을 보낸다.
 *
 * <p><b>실패 정책</b>: 최대 {@value #MAX_ATTEMPTS}회 시도하고, 재시도 사이에 {@value #RETRY_BACKOFF_MS}ms
 * 고정 backoff를 둔다(연결 거부처럼 즉시 실패하는 경우 지연 없이 재시도하면 3회가 사실상 한 순간에
 * 소진돼 재시도의 의미가 없기 때문). {@value #MAX_ATTEMPTS}회 모두 실패하면 예외를 호출자(Kafka 리스너)로
 * 전파하지 않고 ERROR 로그만 남긴 뒤 조용히 끝난다 — 그래야 Kafka 오프셋이 정상 커밋되어 이 메시지를
 * 다시 소비하려 들지 않는다(재전달·별도 보관 없음, 유실 허용). 인기 메뉴 집계 컨슈머와 달리 멱등 처리도
 * 하지 않는데, 외부 전송은 중복 호출이 정확성에 영향을 주지 않기 때문이다.
 *
 * <p>실제로 사용하는 {@link RestClient} 빈(타임아웃 포함)은
 * {@link CollectorRestClientConfig#collectorRestClient}에서 구성한다 — 이 클래스는 그 결과물만
 * 주입받아 쓴다.
 *
 * @see com.coffeeorder.domain.collector.consumer.CollectorEventConsumer
 * @see CollectorRestClientConfig
 * @see MockCollectorController
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorClient {

	/** 전송 실패 시 최대 시도 횟수(최초 시도 포함). Kafka producer의 {@code retries: 3}과 톤을 맞췄다. */
	private static final int MAX_ATTEMPTS = 3;

	/** 재시도 사이 고정 backoff(ms). 부가 경로라 짧게 두되, 즉시 재시도로 인한 무의미한 소진은 막는다. */
	private static final long RETRY_BACKOFF_MS = 200L;

	@Qualifier("collectorRestClient")
	private final RestClient restClient;

	/**
	 * 주문 완료 이벤트를 데이터 수집 플랫폼으로 전송한다.
	 *
	 * <p>{@code event}가 {@code null}이면(예: 이례적인 tombstone 메시지) 아무 것도 하지 않고 즉시
	 * 반환한다 — 이 메서드는 어떤 경우에도 예외를 던지지 않는 것이 계약이므로, null 역참조로 인한
	 * NPE가 그 계약을 깨지 않도록 방어한다.
	 *
	 * <p>정상적인 이벤트라면 최대 {@value #MAX_ATTEMPTS}회까지 HTTP POST를 시도하며, 도중에 성공하면
	 * 즉시 반환한다. 마지막 시도까지 실패하면 ERROR 로그를 남기고 종료한다(이 메서드 자체는 예외를
	 * 던지지 않는다).
	 *
	 * @param event Kafka {@code order-completed} 토픽에서 소비한 주문 완료 이벤트(null 허용)
	 */
	public void send(OrderCompletedEvent event) {
		if (event == null) {
			log.warn("수신한 주문 완료 이벤트가 null이라 데이터 수집 플랫폼 전송을 건너뜀");
			return;
		}

		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				// 요청 변환도 시도 루프 안에서 한다 — 이 메서드는 어떤 경우에도 예외를 던지지 않는
				// 계약이라, 나중에 CollectorTransmitRequest.from에 검증 로직이 추가돼도 그 실패가
				// 이 계약을 깨고 호출자(Kafka 리스너)로 새어나가지 않도록 방어한다(자체 리뷰에서 발견).
				CollectorTransmitRequest request = CollectorTransmitRequest.from(event);
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
					return;
				}
				log.warn("데이터 수집 플랫폼 전송 재시도 (orderGroupId={}, attempt={})",
						event.orderGroupId(), attempt, e);
				if (!sleepBackoff()) {
					return;
				}
			}
		}
	}

	/**
	 * 재시도 사이 {@value #RETRY_BACKOFF_MS}ms만큼 대기한다.
	 *
	 * <p>대기 중 인터럽트되면(예: 컨슈머 스레드 종료) 남은 재시도를 포기하도록 인터럽트 상태를
	 * 복원하고 {@code false}를 반환한다 — 이 클래스는 어떤 경우에도 예외를 던지지 않는다는 계약을
	 * 유지하기 위해서다.
	 *
	 * @return 정상 대기했으면 {@code true}, 인터럽트로 중단됐으면 {@code false}
	 */
	private boolean sleepBackoff() {
		try {
			Thread.sleep(RETRY_BACKOFF_MS);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
