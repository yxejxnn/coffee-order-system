package com.coffeeorder.domain.menu.service;

import com.coffeeorder.domain.menu.dto.MenuResponse;
import com.coffeeorder.domain.menu.repository.MenuRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MenuService {

	private final MenuRepository menuRepository;

	public MenuService(MenuRepository menuRepository) {
		this.menuRepository = menuRepository;
	}

	public List<MenuResponse> getMenus() {
		return menuRepository.findAll().stream()
				.map(MenuResponse::from)
				.toList();
	}
}
