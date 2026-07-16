package com.coffeeorder.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.coffeeorder.domain.menu.dto.MenuResponse;
import com.coffeeorder.domain.menu.entity.Menu;
import com.coffeeorder.domain.menu.repository.MenuRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

	@Mock
	private MenuRepository menuRepository;

	@Test
	void getMenus_mapsAllMenusToResponse() {
		Menu americano = new Menu("아메리카노", 4500);
		Menu latte = new Menu("카페라떼", 5000);
		when(menuRepository.findAll()).thenReturn(List.of(americano, latte));

		List<MenuResponse> result = new MenuService(menuRepository).getMenus();

		assertThat(result).hasSize(2);
		assertThat(result.get(0).name()).isEqualTo("아메리카노");
		assertThat(result.get(0).price()).isEqualTo(4500);
		assertThat(result.get(1).name()).isEqualTo("카페라떼");
	}

	@Test
	void getMenus_returnsEmptyList_whenNoMenus() {
		when(menuRepository.findAll()).thenReturn(List.of());

		List<MenuResponse> result = new MenuService(menuRepository).getMenus();

		assertThat(result).isEmpty();
	}
}
