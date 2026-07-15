package com.coffeeorder.domain.menu.service;

import com.coffeeorder.domain.menu.dto.MenuResponse;
import com.coffeeorder.domain.menu.repository.MenuRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

	private final MenuRepository menuRepository;

	public List<MenuResponse> getMenus() {
		return menuRepository.findAll().stream()
				.map(MenuResponse::from)
				.toList();
	}
}
