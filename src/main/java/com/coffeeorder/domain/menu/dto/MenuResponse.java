package com.coffeeorder.domain.menu.dto;

import com.coffeeorder.domain.menu.entity.Menu;

public record MenuResponse(Long id, String name, Integer price) {

	public static MenuResponse from(Menu menu) {
		return new MenuResponse(
			menu.getId(),
			menu.getName(),
			menu.getPrice());
	}
}
