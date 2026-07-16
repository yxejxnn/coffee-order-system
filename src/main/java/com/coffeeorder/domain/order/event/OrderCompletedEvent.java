package com.coffeeorder.domain.order.event;

public record OrderCompletedEvent(
	String orderGroupId,
	Long memberId,
	Long menuId,
	Long totalPrice
) {
}
