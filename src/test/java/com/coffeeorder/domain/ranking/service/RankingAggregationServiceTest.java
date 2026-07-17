package com.coffeeorder.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import com.coffeeorder.domain.ranking.RankingRedisKeys;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class RankingAggregationServiceTest {

	@Autowired
	private RankingAggregationService rankingAggregationService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	private final List<String> usedIdempotencyKeys = new ArrayList<>();
	private final String rankingKey = RankingRedisKeys.rankingKey(LocalDate.now());

	@AfterEach
	void tearDown() {
		usedIdempotencyKeys.forEach(redisTemplate::delete);
		redisTemplate.delete(rankingKey);
	}

	@Test
	void aggregate_incrementsScore_forFirstEvent() {
		Long menuId = 100L;
		OrderCompletedEvent event = newEvent(menuId);

		rankingAggregationService.aggregate(event);

		Double score = redisTemplate.opsForZSet().score(rankingKey, menuId.toString());
		assertThat(score).isEqualTo(1.0);
	}

	@Test
	void aggregate_incrementsScoreOnceOnly_whenSameOrderGroupIdDeliveredTwice() {
		Long menuId = 101L;
		OrderCompletedEvent event = newEvent(menuId);

		rankingAggregationService.aggregate(event);
		rankingAggregationService.aggregate(event);

		Double score = redisTemplate.opsForZSet().score(rankingKey, menuId.toString());
		assertThat(score).isEqualTo(1.0);
	}

	private OrderCompletedEvent newEvent(Long menuId) {
		String orderGroupId = UUID.randomUUID().toString();
		usedIdempotencyKeys.add(RankingRedisKeys.idempotencyKey(orderGroupId));
		return new OrderCompletedEvent(orderGroupId, 1L, menuId, 4500L);
	}
}
