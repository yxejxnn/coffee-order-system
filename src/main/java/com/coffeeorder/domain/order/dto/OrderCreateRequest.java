package com.coffeeorder.domain.order.dto;

import jakarta.validation.constraints.NotNull;

public record OrderCreateRequest(
	@NotNull(message = "필수입니다")
	Long memberId,
	@NotNull(message = "필수입니다")
	Long menuId,
	Integer quantity
) {
}
