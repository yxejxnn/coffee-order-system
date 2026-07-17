package com.coffeeorder.domain.ranking.service;

import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import com.coffeeorder.domain.ranking.RankingRedisKeys;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingAggregationService {

	private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");
	private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
	private static final Duration RANKING_BUCKET_TTL = Duration.ofDays(8);
	private static final String PROCESSED_MARK = "1";

	private final StringRedisTemplate redisTemplate;

	public void aggregate(OrderCompletedEvent event) {
		String idempotencyKey = RankingRedisKeys.idempotencyKey(event.orderGroupId());
		Boolean firstSeen = redisTemplate.opsForValue()
				.setIfAbsent(idempotencyKey, PROCESSED_MARK, IDEMPOTENCY_TTL);
		if (!Boolean.TRUE.equals(firstSeen)) {
			log.info("중복 이벤트 스킵 (orderGroupId={})", event.orderGroupId());
			return;
		}

		try {
			String rankingKey = RankingRedisKeys.rankingKey(LocalDate.now(RANKING_ZONE));
			redisTemplate.opsForZSet().incrementScore(rankingKey, event.menuId().toString(), 1);
			redisTemplate.expire(rankingKey, RANKING_BUCKET_TTL);
		} catch (RuntimeException e) {
			// 멱등 마킹 이후 집계에 실패하면 마킹을 롤백해, Kafka 재전달 시 "이미 처리됨"으로 잘못
			// 스킵되어 주문이 영구 누락되는 대신 정상적으로 재시도되게 한다.
			redisTemplate.delete(idempotencyKey);
			throw e;
		}
	}
}
