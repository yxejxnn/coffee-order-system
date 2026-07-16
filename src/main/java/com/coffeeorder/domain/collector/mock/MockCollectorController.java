package com.coffeeorder.domain.collector.mock;

import com.coffeeorder.domain.collector.dto.CollectorTransmitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// 외부 데이터 수집 플랫폼 흉내이므로 우리 API 응답 규약(ApiResponse)을 따르지 않는다.
@RestController
public class MockCollectorController {

	private static final Logger log = LoggerFactory.getLogger(MockCollectorController.class);

	@PostMapping("/mock/collector/orders")
	public ResponseEntity<Void> receive(@RequestBody CollectorTransmitRequest request) {
		log.info("[Mock 데이터 수집 플랫폼] 수신: memberId={}, menuId={}, amount={}",
				request.memberId(), request.menuId(), request.amount());
		return ResponseEntity.ok().build();
	}
}
