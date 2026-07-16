package com.coffeeorder.domain.menu.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.coffeeorder.common.response.ApiResponse;
import com.coffeeorder.domain.menu.dto.MenuResponse;
import com.coffeeorder.domain.menu.service.MenuService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MenuControllerTest {

	@Mock
	private MenuService menuService;

	@Test
	void getMenus_returns200WithMenuList() {
		MenuResponse menu = new MenuResponse(1L, "아메리카노", 4500);
		when(menuService.getMenus()).thenReturn(List.of(menu));

		ResponseEntity<ApiResponse<List<MenuResponse>>> response = new MenuController(menuService).getMenus();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getCode()).isEqualTo("SUCCESS");
		assertThat(response.getBody().getData())
				.extracting(MenuResponse::id, MenuResponse::name, MenuResponse::price)
				.containsExactly(tuple(1L, "아메리카노", 4500));
	}

	@Test
	void getMenus_returns200WithEmptyList_whenNoMenus() {
		when(menuService.getMenus()).thenReturn(List.of());

		ResponseEntity<ApiResponse<List<MenuResponse>>> response = new MenuController(menuService).getMenus();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getData()).isEmpty();
	}
}
