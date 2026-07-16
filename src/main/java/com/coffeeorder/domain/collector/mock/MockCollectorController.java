package com.coffeeorder.domain.collector.mock;

import com.coffeeorder.domain.collector.dto.CollectorTransmitRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실제 존재하지 않는 외부 "데이터 수집 플랫폼"을 대신하는 Mock 수신 엔드포인트.
 *
 * <p>발제 요구사항 3(주문내역을 데이터 수집 플랫폼으로 실시간 전송)을 검증하려면 뭔가는 실제로
 * 요청을 받아야 하는데, 과제 특성상 진짜 외부 시스템이 없다. 그래서 같은 애플리케이션 안에 이
 * 자리를 대신하는 컨트롤러를 두어, {@code docker compose up} + {@code bootRun}만으로 주문 →
 * Kafka → {@link com.coffeeorder.domain.collector.consumer.CollectorEventConsumer} →
 * {@link com.coffeeorder.domain.collector.client.CollectorClient} → 여기까지 전체 흐름을 실제
 * HTTP 통신으로 눈으로 확인할 수 있게 했다. 받은 페이로드를 로그로 남기고 200을 반환할 뿐,
 * 저장·검증 로직은 없는 순수 Mock이다.
 *
 * <p>외부 데이터 수집 플랫폼 흉내이므로 우리 API 응답 규약({@code ApiResponse<T>})을 따르지 않는다
 * — 실제 외부 시스템이 우리 응답 봉투를 알 리 없기 때문이다(Plan 단계에서 승인된 의도적 예외).
 *
 * @see com.coffeeorder.domain.collector.client.CollectorClient
 */
@Slf4j
@RestController
public class MockCollectorController {

	/** 이 Mock 엔드포인트의 경로. {@link com.coffeeorder.domain.collector.client.CollectorClient}가 그대로 참조한다. */
	public static final String ORDERS_PATH = "/mock/collector/orders";

	/**
	 * 데이터 수집 플랫폼 전송 요청을 받아 로그로 남기고 200을 반환한다.
	 *
	 * @param request 전송된 주문 정보(memberId, menuId, amount)
	 * @return 항상 {@code 200 OK}(바디 없음)
	 */
	@PostMapping(ORDERS_PATH)
	public ResponseEntity<Void> receive(@RequestBody CollectorTransmitRequest request) {
		log.info("[Mock 데이터 수집 플랫폼] 수신: memberId={}, menuId={}, amount={}",
				request.memberId(), request.menuId(), request.amount());
		return ResponseEntity.ok().build();
	}
}
