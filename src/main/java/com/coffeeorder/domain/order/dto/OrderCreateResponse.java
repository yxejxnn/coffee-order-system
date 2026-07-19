package com.coffeeorder.domain.order.dto;

import com.coffeeorder.domain.order.entity.Order;

public record OrderCreateResponse(
	String orderGroupId,
	Long memberId,
	Long menuId,
	Integer quantity,
	Long totalPrice,
	Long balance
) {

	public static OrderCreateResponse from(Order order, Long balance) {
		return new OrderCreateResponse(
			order.getOrderGroupId(),
			order.getMemberId(),
			order.getMenuId(),
			order.getQuantity(),
			order.getTotalPrice(),
			balance);
	}
}
