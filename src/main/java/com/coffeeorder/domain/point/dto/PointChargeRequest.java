package com.coffeeorder.domain.point.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PointChargeRequest {

	@NotNull
	private Long memberId;

	@NotNull
	private Long amount;
}
