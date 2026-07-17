package com.coffeeorder.domain.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import com.coffeeorder.domain.ranking.RankingRedisKeys;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class RankingAggregationServiceConcurrencyTest {

	private static final int THREAD_COUNT = 30;

	@Autowired
	private RankingAggregationService rankingAggregationService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	private final List<String> usedIdempotencyKeys = new CopyOnWriteArrayList<>();
	private final String rankingKey = RankingRedisKeys.rankingKey(LocalDate.now());

	@AfterEach
	void tearDown() {
		usedIdempotencyKeys.forEach(redisTemplate::delete);
		redisTemplate.delete(rankingKey);
	}

	@Test
	void aggregate_distinctOrderGroupIdsConcurrently_scoreEqualsThreadCount() throws InterruptedException {
		Long menuId = 200L;
		List<OrderCompletedEvent> events = Collections.nCopies(THREAD_COUNT, null).stream()
				.map(unused -> newEvent(menuId))
				.toList();

		runConcurrently(events);

		Double score = redisTemplate.opsForZSet().score(rankingKey, menuId.toString());
		assertThat(score).isEqualTo((double) THREAD_COUNT);
	}

	@Test
	void aggregate_sameOrderGroupIdConcurrently_scoreIncrementsOnce() throws InterruptedException {
		Long menuId = 201L;
		OrderCompletedEvent event = newEvent(menuId);
		List<OrderCompletedEvent> events = Collections.nCopies(THREAD_COUNT, event);

		runConcurrently(events);

		Double score = redisTemplate.opsForZSet().score(rankingKey, menuId.toString());
		assertThat(score).isEqualTo(1.0);
	}

	private void runConcurrently(List<OrderCompletedEvent> events) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(events.size());
		for (OrderCompletedEvent event : events) {
			executor.submit(() -> {
				try {
					rankingAggregationService.aggregate(event);
				} finally {
					latch.countDown();
				}
			});
		}
		boolean completedInTime = latch.await(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(completedInTime).as("모든 집계 요청이 타임아웃 없이 끝나야 한다").isTrue();
	}

	private OrderCompletedEvent newEvent(Long menuId) {
		String orderGroupId = UUID.randomUUID().toString();
		usedIdempotencyKeys.add(RankingRedisKeys.idempotencyKey(orderGroupId));
		return new OrderCompletedEvent(orderGroupId, 1L, menuId, 4500L);
	}
}
