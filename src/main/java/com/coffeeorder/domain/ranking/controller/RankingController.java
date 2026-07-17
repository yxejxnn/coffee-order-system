package com.coffeeorder.domain.ranking.controller;

import com.coffeeorder.common.response.ApiResponse;
import com.coffeeorder.domain.ranking.dto.PopularMenuResponse;
import com.coffeeorder.domain.ranking.service.RankingQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class RankingController {

	private final RankingQueryService rankingQueryService;

	@GetMapping("/popular")
	public ResponseEntity<ApiResponse<List<PopularMenuResponse>>> getPopularMenus() {
		return ResponseEntity.ok(ApiResponse.ok(rankingQueryService.getPopularMenus()));
	}
}
