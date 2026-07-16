package com.coffeeorder.domain.point.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.coffeeorder.common.response.ApiResponse;
import com.coffeeorder.domain.point.dto.PointChargeRequest;
import com.coffeeorder.domain.point.dto.PointChargeResponse;
import com.coffeeorder.domain.point.service.PointService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PointControllerTest {

	@Mock
	private PointService pointService;

	@Test
	void charge_returns200WithChargedBalance() {
		PointChargeRequest request = new PointChargeRequest();
		request.setMemberId(1L);
		request.setAmount(3000L);
		when(pointService.charge(1L, 3000L)).thenReturn(new PointChargeResponse(1L, 8000L));

		ResponseEntity<ApiResponse<PointChargeResponse>> response = new PointController(pointService).charge(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getCode()).isEqualTo("SUCCESS");
		assertThat(response.getBody().getData().getMemberId()).isEqualTo(1L);
		assertThat(response.getBody().getData().getBalance()).isEqualTo(8000L);
	}
}
