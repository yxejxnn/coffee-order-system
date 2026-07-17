package com.coffeeorder.domain.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

public final class RankingRedisKeys {

	public static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");

	private static final String RANKING_KEY_PREFIX = "menu:ranking:";
	private static final String IDEMPOTENCY_KEY_PREFIX = "ranking:idempotency:";
	private static final int RECENT_WINDOW_DAYS = 7;

	public static String rankingKey(LocalDate date) {
		return RANKING_KEY_PREFIX + date;
	}

	public static String idempotencyKey(String orderGroupId) {
		return IDEMPOTENCY_KEY_PREFIX + orderGroupId;
	}

	public static List<String> recentRankingKeys(LocalDate today) {
		return IntStream.range(0, RECENT_WINDOW_DAYS)
				.mapToObj(offset -> rankingKey(today.minusDays(offset)))
				.toList();
	}

	private RankingRedisKeys() {
	}
}
