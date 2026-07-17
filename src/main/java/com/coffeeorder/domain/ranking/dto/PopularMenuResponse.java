package com.coffeeorder.domain.ranking.dto;

import com.coffeeorder.domain.menu.entity.Menu;

public record PopularMenuResponse(
	Integer rank,
	Long menuId,
	String name,
	Long orderCount
) {

	public static PopularMenuResponse from(Integer rank, Menu menu, Long orderCount) {
		return new PopularMenuResponse(
			rank,
			menu.getId(),
			menu.getName(),
			orderCount);
	}
}
