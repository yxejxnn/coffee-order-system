package com.coffeeorder.domain.menu.controller;

import com.coffeeorder.common.response.ApiResponse;
import com.coffeeorder.domain.menu.dto.MenuResponse;
import com.coffeeorder.domain.menu.service.MenuService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

	private final MenuService menuService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<MenuResponse>>> getMenus() {
		return ResponseEntity.ok(ApiResponse.ok(menuService.getMenus()));
	}
}
