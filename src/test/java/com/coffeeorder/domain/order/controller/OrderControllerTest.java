package com.coffeeorder.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.coffeeorder.common.response.ApiResponse;
import com.coffeeorder.domain.order.dto.OrderCreateRequest;
import com.coffeeorder.domain.order.dto.OrderCreateResponse;
import com.coffeeorder.domain.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

	@Mock
	private OrderService orderService;

	@Test
	void create_returns201WithOrderResult() {
		OrderCreateRequest request = new OrderCreateRequest(1L, 2L, 1);
		OrderCreateResponse expected = new OrderCreateResponse("group-uuid", 1L, 2L, 1, 4500L, 10000L);
		when(orderService.create(1L, 2L, 1, null)).thenReturn(expected);

		ResponseEntity<ApiResponse<OrderCreateResponse>> response = new OrderController(orderService).create(null, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getCode()).isEqualTo("SUCCESS");
		assertThat(response.getBody().getData().orderGroupId()).isEqualTo("group-uuid");
		assertThat(response.getBody().getData().totalPrice()).isEqualTo(4500L);
		assertThat(response.getBody().getData().balance()).isEqualTo(10000L);
	}

	@Test
	void create_passesIdempotencyKeyHeaderToService() {
		OrderCreateRequest request = new OrderCreateRequest(1L, 2L, 1);
		OrderCreateResponse expected = new OrderCreateResponse("group-uuid", 1L, 2L, 1, 4500L, 10000L);
		when(orderService.create(1L, 2L, 1, "retry-key")).thenReturn(expected);

		ResponseEntity<ApiResponse<OrderCreateResponse>> response =
				new OrderController(orderService).create("retry-key", request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}
}
