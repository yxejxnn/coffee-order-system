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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

		List<TypedTuple<String>> top = unioned.stream()
				.sorted(Comparator.<TypedTuple<String>>comparingDouble(tuple -> tuple.getScore() == null ? 0 : tuple.getScore())
						.reversed()
						.thenComparing(tuple -> Long.parseLong(tuple.getValue())))
				.limit(POPULAR_MENU_LIMIT)
				.toList();

		List<Long> menuIds = top.stream()
				.map(tuple -> Long.parseLong(tuple.getValue()))
				.toList();
		Map<Long, Menu> menuById = menuRepository.findAllById(menuIds).stream()
				.collect(Collectors.toMap(Menu::getId, menu -> menu));

		List<PopularMenuResponse> responses = new ArrayList<>();
		int rank = 1;
		for (TypedTuple<String> tuple : top) {
			Long menuId = Long.parseLong(tuple.getValue());
			Menu menu = menuById.get(menuId);
			responses.add(new PopularMenuResponse(
					rank++,
					menuId,
					menu.getName(),
					tuple.getScore().longValue()));
		}
		return responses;
	}
}
