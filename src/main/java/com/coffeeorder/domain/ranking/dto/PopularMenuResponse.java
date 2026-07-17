package com.coffeeorder.domain.ranking.dto;

public record PopularMenuResponse(
	Integer rank,
	Long menuId,
	String name,
	Long orderCount
) {
}
