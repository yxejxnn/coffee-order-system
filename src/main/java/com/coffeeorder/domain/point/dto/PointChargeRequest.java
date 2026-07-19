package com.coffeeorder.domain.point.dto;

import jakarta.validation.constraints.NotNull;

public record PointChargeRequest(
	@NotNull(message = "필수입니다")
	Long memberId,
	@NotNull(message = "필수입니다")
	Long amount
) {
}
