package com.coffeeorder.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coffeeorder.domain.menu.entity.Menu;
import com.coffeeorder.domain.menu.repository.MenuRepository;
import com.coffeeorder.domain.ranking.RankingRedisKeys;
import com.coffeeorder.domain.ranking.dto.PopularMenuResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class RankingQueryServiceTest {

	@Autowired
	private RankingQueryService rankingQueryService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private MenuRepository menuRepository;

	private final LocalDate today = LocalDate.now(RankingRedisKeys.RANKING_ZONE);
	private final List<Runnable> cleanupTasks = new ArrayList<>();

	@AfterEach
	void tearDown() {
		cleanupTasks.forEach(Runnable::run);
	}

	@Test
	void getPopularMenus_includesRealMenuJoinedFromRealRedisUnion() {
		Menu menu = menuRepository.save(new Menu("실HTTP검증용메뉴", 4500));
		seed(RankingRedisKeys.rankingKey(today), menu.getId(), 999.0);

		List<PopularMenuResponse> result = rankingQueryService.getPopularMenus();

		assertThat(result)
				.filteredOn(r -> r.menuId().equals(menu.getId()))
				.hasSize(1)
				.first()
				.satisfies(r -> {
					assertThat(r.name()).isEqualTo("실HTTP검증용메뉴");
					assertThat(r.orderCount()).isEqualTo(999L);
				});
	}

	@Test
	void getPopularMenus_includesDayMinusSix_excludesDayMinusSeven() {
		Menu includedMenu = menuRepository.save(new Menu("7일경계-포함", 4500));
		Menu excludedMenu = menuRepository.save(new Menu("7일경계-제외", 4500));
		seed(RankingRedisKeys.rankingKey(today.minusDays(6)), includedMenu.getId(), 1_000_000.0);
		seed(RankingRedisKeys.rankingKey(today.minusDays(7)), excludedMenu.getId(), 1_000_000.0);

		List<PopularMenuResponse> result = rankingQueryService.getPopularMenus();

		assertThat(result).extracting(PopularMenuResponse::menuId)
				.contains(includedMenu.getId())
				.doesNotContain(excludedMenu.getId());
	}

	private void seed(String rankingKey, Long menuId, double score) {
		redisTemplate.opsForZSet().incrementScore(rankingKey, menuId.toString(), score);
		cleanupTasks.add(() -> redisTemplate.opsForZSet().remove(rankingKey, menuId.toString()));
	}
}
