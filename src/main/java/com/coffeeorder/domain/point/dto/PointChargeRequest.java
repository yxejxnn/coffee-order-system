package com.coffeeorder.domain.point.dto;

import jakarta.validation.constraints.NotNull;

public record PointChargeRequest(
	@NotNull Long memberId,
	@NotNull Long amount
) {
}
