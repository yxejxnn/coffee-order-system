package com.coffeeorder.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coffeeorder.domain.menu.entity.Menu;
import com.coffeeorder.domain.menu.repository.MenuRepository;
import com.coffeeorder.domain.ranking.RankingRedisKeys;
import com.coffeeorder.domain.ranking.dto.PopularMenuResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RankingQueryServiceUnitTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ZSetOperations<String, String> zSetOperations;

	@Mock
	private MenuRepository menuRepository;

	private RankingQueryService service() {
		return new RankingQueryService(redisTemplate, menuRepository);
	}

	@Test
	void getPopularMenus_returnsEmptyList_whenUnionIsNull() {
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
		when(zSetOperations.unionWithScores(anyString(), anyList())).thenReturn(null);

		List<PopularMenuResponse> result = service().getPopularMenus();

		assertThat(result).isEmpty();
	}

	@Test
	void getPopularMenus_unionsExactlySevenRecentDailyKeys() {
		LocalDate today = LocalDate.now(RankingRedisKeys.RANKING_ZONE);
		List<String> expectedKeys = RankingRedisKeys.recentRankingKeys(today);
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
		when(zSetOperations.unionWithScores(eq(expectedKeys.get(0)), eq(expectedKeys.subList(1, expectedKeys.size()))))
				.thenReturn(Set.of());

		service().getPopularMenus();

		verify(zSetOperations).unionWithScores(expectedKeys.get(0), expectedKeys.subList(1, expectedKeys.size()));
	}

	@Test
	void getPopularMenus_sortsByScoreDesc_thenMenuIdAscOnTie_andLimitsToTopThree() {
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
		when(zSetOperations.unionWithScores(anyString(), anyList())).thenReturn(Set.of(
				scoreOf(4L, 1.0),
				scoreOf(2L, 3.0),
				scoreOf(1L, 3.0),
				scoreOf(3L, 10.0)));
		when(menuRepository.findAllById(anyIterable())).thenReturn(List.of(
				menuWithId(1L, "메뉴1"),
				menuWithId(2L, "메뉴2"),
				menuWithId(3L, "메뉴3")));

		List<PopularMenuResponse> result = service().getPopularMenus();

		assertThat(result)
				.extracting(PopularMenuResponse::rank, PopularMenuResponse::menuId, PopularMenuResponse::name, PopularMenuResponse::orderCount)
				.containsExactly(
						tuple(1, 3L, "메뉴3", 10L),
						tuple(2, 1L, "메뉴1", 3L),
						tuple(3, 2L, "메뉴2", 3L));
	}

	@Test
	void getPopularMenus_returnsFewerThanThree_whenOnlyTwoCandidatesExist() {
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
		when(zSetOperations.unionWithScores(anyString(), anyList())).thenReturn(Set.of(
				scoreOf(1L, 5.0),
				scoreOf(2L, 2.0)));
		when(menuRepository.findAllById(anyIterable())).thenReturn(List.of(
				menuWithId(1L, "메뉴1"),
				menuWithId(2L, "메뉴2")));

		List<PopularMenuResponse> result = service().getPopularMenus();

		assertThat(result)
				.extracting(PopularMenuResponse::menuId, PopularMenuResponse::orderCount)
				.containsExactly(
						tuple(1L, 5L),
						tuple(2L, 2L));
	}

	@Test
	void getPopularMenus_skipsRankedMenuId_whenMenuNoLongerExistsInDb() {
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
		when(zSetOperations.unionWithScores(anyString(), anyList())).thenReturn(Set.of(
				scoreOf(1L, 5.0),
				scoreOf(2L, 3.0)));
		when(menuRepository.findAllById(anyIterable())).thenReturn(List.of(menuWithId(1L, "메뉴1")));

		List<PopularMenuResponse> result = service().getPopularMenus();

		assertThat(result)
				.extracting(PopularMenuResponse::rank, PopularMenuResponse::menuId)
				.containsExactly(tuple(1, 1L));
	}

	private TypedTuple<String> scoreOf(Long menuId, double score) {
		return new DefaultTypedTuple<>(menuId.toString(), score);
	}

	private Menu menuWithId(Long id, String name) {
		// Menu.id는 @GeneratedValue라 DB에 저장해야만 채워진다. 이 클래스는 순수 Mockito
		// 유닛 테스트라 DB 없이 menuById 조인 로직(Menu::getId 기준 매핑)을 검증하려면
		// id를 직접 주입할 수밖에 없다.
		Menu menu = new Menu(name, 1000);
		ReflectionTestUtils.setField(menu, "id", id);
		return menu;
	}
}
