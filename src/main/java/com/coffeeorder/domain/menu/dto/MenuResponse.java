package com.coffeeorder.domain.menu.dto;

import com.coffeeorder.domain.menu.entity.Menu;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class MenuResponse {

	private final Long id;
	private final String name;
	private final Integer price;

	public static MenuResponse from(Menu menu) {
		return new MenuResponse(
			menu.getId(),
			menu.getName(),
			menu.getPrice());
	}
}
