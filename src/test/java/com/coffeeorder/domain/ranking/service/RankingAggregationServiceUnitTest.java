package com.coffeeorder.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import com.coffeeorder.domain.ranking.RankingRedisKeys;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class RankingAggregationServiceUnitTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private ZSetOperations<String, String> zSetOperations;

	private RankingAggregationService rankingAggregationService;

	@BeforeEach
	void setUp() {
		rankingAggregationService = new RankingAggregationService(redisTemplate);
	}

	@Test
	void aggregate_rollsBackIdempotencyMark_whenIncrementFails() {
		OrderCompletedEvent event = new OrderCompletedEvent("group-1", 1L, 2L, 4500L);
		String idempotencyKey = RankingRedisKeys.idempotencyKey(event.orderGroupId());

		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
		when(zSetOperations.incrementScore(anyString(), anyString(), any(Double.class)))
				.thenThrow(new RuntimeException("redis down"));

		assertThatThrownBy(() -> rankingAggregationService.aggregate(event))
				.isInstanceOf(RuntimeException.class);

		verify(redisTemplate).delete(idempotencyKey);
	}
}
