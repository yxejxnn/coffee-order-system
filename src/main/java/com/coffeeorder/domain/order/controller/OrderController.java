package com.coffeeorder.domain.order.controller;

import com.coffeeorder.common.response.ApiResponse;
import com.coffeeorder.domain.order.dto.OrderCreateRequest;
import com.coffeeorder.domain.order.dto.OrderCreateResponse;
import com.coffeeorder.domain.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	@PostMapping
	public ResponseEntity<ApiResponse<OrderCreateResponse>> create(
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody OrderCreateRequest request) {
		OrderCreateResponse response = orderService.create(
				request.memberId(), request.menuId(), request.quantity(), idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
	}
}
