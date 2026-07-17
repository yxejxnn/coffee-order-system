package com.coffeeorder.domain.ranking;

import java.time.LocalDate;

public final class RankingRedisKeys {

	private static final String RANKING_KEY_PREFIX = "menu:ranking:";
	private static final String IDEMPOTENCY_KEY_PREFIX = "ranking:idempotency:";

	public static String rankingKey(LocalDate date) {
		return RANKING_KEY_PREFIX + date;
	}

	public static String idempotencyKey(String orderGroupId) {
		return IDEMPOTENCY_KEY_PREFIX + orderGroupId;
	}

	private RankingRedisKeys() {
	}
}
