package com.coffeeorder.domain.collector.dto;

import com.coffeeorder.domain.order.event.OrderCompletedEvent;

public record CollectorTransmitRequest(
	Long memberId,
	Long menuId,
	Long amount
) {

	public static CollectorTransmitRequest from(OrderCompletedEvent event) {
		return new CollectorTransmitRequest(
			event.memberId(),
			event.menuId(),
			event.totalPrice());
	}
}
