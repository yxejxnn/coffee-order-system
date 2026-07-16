package com.coffeeorder.domain.point.dto;

import com.coffeeorder.domain.point.entity.Point;

public record PointChargeResponse(
	Long memberId,
	Long balance
) {

	public static PointChargeResponse from(Point point) {
		return new PointChargeResponse(
			point.getMemberId(),
			point.getBalance());
	}
}
