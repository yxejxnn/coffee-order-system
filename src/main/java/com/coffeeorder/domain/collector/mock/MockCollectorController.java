package com.coffeeorder.domain.collector.mock;

import com.coffeeorder.domain.collector.dto.CollectorTransmitRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// 외부 데이터 수집 플랫폼 흉내이므로 우리 API 응답 규약(ApiResponse)을 따르지 않는다.
@Slf4j
@RestController
public class MockCollectorController {

	public static final String ORDERS_PATH = "/mock/collector/orders";

	@PostMapping(ORDERS_PATH)
	public ResponseEntity<Void> receive(@RequestBody CollectorTransmitRequest request) {
		log.info("[Mock 데이터 수집 플랫폼] 수신: memberId={}, menuId={}, amount={}",
				request.memberId(), request.menuId(), request.amount());
		return ResponseEntity.ok().build();
	}
}
