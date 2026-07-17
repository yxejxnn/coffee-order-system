package com.coffeeorder.domain.ranking.consumer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.coffeeorder.config.KafkaTopics;
import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import com.coffeeorder.domain.ranking.service.RankingAggregationService;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = { KafkaTopics.ORDER_COMPLETED })
class RankingEventConsumerTest {

	@Autowired
	private EmbeddedKafkaBroker embeddedKafkaBroker;

	@MockitoBean
	private RankingAggregationService rankingAggregationService;

	private KafkaProducer<String, String> producer;

	@BeforeEach
	void setUp() {
		Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		producer = new KafkaProducer<>(props);
	}

	@AfterEach
	void tearDown() {
		producer.close();
	}

	@Test
	void consume_delegatesEventToRankingAggregationService() {
		OrderCompletedEvent event = new OrderCompletedEvent("group-1", 1L, 2L, 4500L);
		String json = String.format(
				"{\"orderGroupId\":\"%s\",\"memberId\":%d,\"menuId\":%d,\"totalPrice\":%d}",
				event.orderGroupId(), event.memberId(), event.menuId(), event.totalPrice());

		producer.send(new ProducerRecord<>(KafkaTopics.ORDER_COMPLETED, event.orderGroupId(), json));

		verify(rankingAggregationService, timeout(10000)).aggregate(eq(event));
	}
}
