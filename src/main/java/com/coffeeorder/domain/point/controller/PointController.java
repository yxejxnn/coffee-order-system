package com.coffeeorder.domain.point.controller;

import com.coffeeorder.common.response.ApiResponse;
import com.coffeeorder.domain.point.dto.PointChargeRequest;
import com.coffeeorder.domain.point.dto.PointChargeResponse;
import com.coffeeorder.domain.point.service.PointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

	private final PointService pointService;

	@PostMapping("/charge")
	public ResponseEntity<ApiResponse<PointChargeResponse>> charge(@Valid @RequestBody PointChargeRequest request) {
		PointChargeResponse response = pointService.charge(request.getMemberId(), request.getAmount());
		return ResponseEntity.ok(ApiResponse.ok(response));
	}
}
