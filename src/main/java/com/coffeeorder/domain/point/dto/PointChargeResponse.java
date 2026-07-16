package com.coffeeorder.domain.point.dto;

import com.coffeeorder.domain.point.entity.Point;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class PointChargeResponse {

	private final Long memberId;
	private final Long balance;

	public static PointChargeResponse from(Point point) {
		return new PointChargeResponse(point.getMemberId(), point.getBalance());
	}
}
