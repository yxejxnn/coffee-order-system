package com.coffeeorder.domain.ranking.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.coffeeorder.config.KafkaTopics;
import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import com.coffeeorder.domain.ranking.service.RankingAggregationService;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
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
@EmbeddedKafka(partitions = 1, topics = { KafkaTopics.ORDER_COMPLETED, KafkaTopics.ORDER_COMPLETED_DLT })
class RankingEventConsumerTest {

	@Autowired
	private EmbeddedKafkaBroker embeddedKafkaBroker;

	@MockitoBean
	private RankingAggregationService rankingAggregationService;

	private KafkaProducer<String, String> producer;
	private KafkaConsumer<String, String> dltConsumer;

	@BeforeEach
	void setUp() {
		Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
		props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		producer = new KafkaProducer<>(props);

		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("ranking-dlt-test-group", "true", embeddedKafkaBroker);
		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		dltConsumer = new KafkaConsumer<>(consumerProps);
		embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, KafkaTopics.ORDER_COMPLETED_DLT);
	}

	@AfterEach
	void tearDown() {
		producer.close();
		dltConsumer.close();
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

	@Test
	void consume_publishesToDeadLetterTopic_afterRetriesExhausted() {
		// Redis 장애 등으로 집계가 계속 실패하는 상황을 재현(#41) — 재시도 소진 후 유실되지 않고
		// DLT 토픽에 도착해야 한다.
		OrderCompletedEvent event = new OrderCompletedEvent("group-dlt", 3L, 4L, 5000L);
		String json = String.format(
				"{\"orderGroupId\":\"%s\",\"memberId\":%d,\"menuId\":%d,\"totalPrice\":%d}",
				event.orderGroupId(), event.memberId(), event.menuId(), event.totalPrice());
		doThrow(new RuntimeException("Redis 장애 시뮬레이션"))
				.when(rankingAggregationService).aggregate(eq(event));

		producer.send(new ProducerRecord<>(KafkaTopics.ORDER_COMPLETED, event.orderGroupId(), json));

		// FixedBackOff(500L, 2) → 최초 시도 포함 총 3회 호출 후 DLT로 넘어가야 한다.
		verify(rankingAggregationService, timeout(15000).times(3)).aggregate(eq(event));

		ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(10));
		boolean found = false;
		for (var record : records) {
			if (record.value() != null && record.value().contains(event.orderGroupId())) {
				found = true;
			}
		}
		assertThat(found).as("재시도 소진 후 원본 이벤트가 DLT 토픽에 도착해야 한다").isTrue();
	}
}
