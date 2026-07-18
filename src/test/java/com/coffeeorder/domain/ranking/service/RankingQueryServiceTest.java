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
		// 이 Redis는 로컬 dev용 docker-compose를 다른 테스트·수동 bootRun과 공유해서
		// "오늘" 버킷에 소규모 실제/테스트 카운트가 섞여 있을 수 있다. includedMenu가
		// 실제 top-3 안에 들어야 검증되는 케이스라, 그 잡음을 확실히 이기도록 점수를
		// 크게 잡는다(제외 쪽은 날짜 자체가 union 쿼리에 안 들어가므로 점수와 무관하게
		// 제외되지만, 대칭성을 위해 동일하게 크게 둠).
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
