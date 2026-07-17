package com.coffeeorder.domain.ranking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.coffeeorder.common.response.ApiResponse;
import com.coffeeorder.domain.ranking.dto.PopularMenuResponse;
import com.coffeeorder.domain.ranking.service.RankingQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class RankingControllerTest {

	@Mock
	private RankingQueryService rankingQueryService;

	@Test
	void getPopularMenus_returns200WithRankedList() {
		PopularMenuResponse menu = new PopularMenuResponse(1, 2L, "카페라떼", 128L);
		when(rankingQueryService.getPopularMenus()).thenReturn(List.of(menu));

		ResponseEntity<ApiResponse<List<PopularMenuResponse>>> response =
				new RankingController(rankingQueryService).getPopularMenus();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getCode()).isEqualTo("SUCCESS");
		assertThat(response.getBody().getData())
				.extracting(PopularMenuResponse::rank, PopularMenuResponse::menuId, PopularMenuResponse::name, PopularMenuResponse::orderCount)
				.containsExactly(tuple(1, 2L, "카페라떼", 128L));
	}

	@Test
	void getPopularMenus_returns200WithEmptyList_whenNoRankingData() {
		when(rankingQueryService.getPopularMenus()).thenReturn(List.of());

		ResponseEntity<ApiResponse<List<PopularMenuResponse>>> response =
				new RankingController(rankingQueryService).getPopularMenus();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getData()).isEmpty();
	}
}
