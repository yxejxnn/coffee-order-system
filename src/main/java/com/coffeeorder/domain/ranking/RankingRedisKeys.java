package com.coffeeorder.domain.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

public final class RankingRedisKeys {

	/**
	 * 집계(쓰기)·조회(읽기) 양쪽이 "오늘"을 반드시 같은 타임존 기준으로 계산해야 일자 버킷
	 * 경계가 어긋나지 않는다. 한쪽만 바뀌면 자정 전후 몇 초 사이의 주문이 서로 다른 날짜
	 * 버킷으로 갈려 카운트가 틀어질 수 있어 상수를 한 곳에서만 정의한다.
	 */
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
