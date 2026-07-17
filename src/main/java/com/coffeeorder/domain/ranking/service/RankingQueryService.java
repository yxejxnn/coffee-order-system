package com.coffeeorder.domain.ranking.service;

import com.coffeeorder.domain.menu.entity.Menu;
import com.coffeeorder.domain.menu.repository.MenuRepository;
import com.coffeeorder.domain.ranking.RankingRedisKeys;
import com.coffeeorder.domain.ranking.dto.PopularMenuResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingQueryService {

	private static final int POPULAR_MENU_LIMIT = 3;

	private final StringRedisTemplate redisTemplate;
	private final MenuRepository menuRepository;

	public List<PopularMenuResponse> getPopularMenus() {
		LocalDate today = LocalDate.now(RankingRedisKeys.RANKING_ZONE);
		List<String> keys = RankingRedisKeys.recentRankingKeys(today);

		Set<TypedTuple<String>> unioned = redisTemplate.opsForZSet()
				.unionWithScores(keys.get(0), keys.subList(1, keys.size()));
		if (unioned == null) {
			return List.of();
		}

		List<RankedMenu> top = unioned.stream()
				.map(tuple -> new RankedMenu(Long.parseLong(tuple.getValue()), tuple.getScore()))
				.sorted(Comparator.comparingDouble(RankedMenu::score)
						.reversed()
						.thenComparing(RankedMenu::menuId))
				.limit(POPULAR_MENU_LIMIT)
				.toList();

		Map<Long, Menu> menuById = menuRepository.findAllById(top.stream().map(RankedMenu::menuId).toList()).stream()
				.collect(Collectors.toMap(Menu::getId, menu -> menu));

		List<PopularMenuResponse> responses = new ArrayList<>();
		int rank = 1;
		for (RankedMenu rankedMenu : top) {
			Menu menu = menuById.get(rankedMenu.menuId());
			if (menu == null) {
				log.warn("랭킹 데이터에는 있으나 메뉴 조회 실패, 스킵 (menuId={})", rankedMenu.menuId());
				continue;
			}
			responses.add(PopularMenuResponse.from(rank++, menu, rankedMenu.score().longValue()));
		}
		return responses;
	}

	private record RankedMenu(Long menuId, Double score) {
	}
}
