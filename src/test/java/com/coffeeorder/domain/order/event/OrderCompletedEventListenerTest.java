package com.coffeeorder.domain.order.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coffeeorder.common.exception.CoffeeOrderException;
import com.coffeeorder.config.KafkaTopics;
import com.coffeeorder.domain.member.entity.Member;
import com.coffeeorder.domain.member.repository.MemberRepository;
import com.coffeeorder.domain.menu.entity.Menu;
import com.coffeeorder.domain.menu.repository.MenuRepository;
import com.coffeeorder.domain.order.dto.OrderCreateResponse;
import com.coffeeorder.domain.order.service.OrderService;
import com.coffeeorder.domain.point.entity.Point;
import com.coffeeorder.domain.point.repository.PointRepository;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = { KafkaTopics.ORDER_COMPLETED })
class OrderCompletedEventListenerTest {

	@Autowired
	private OrderService orderService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private MenuRepository menuRepository;

	@Autowired
	private PointRepository pointRepository;

	@Autowired
	private EmbeddedKafkaBroker embeddedKafkaBroker;

	private KafkaConsumer<String, String> consumer;

	@BeforeEach
	void setUp() {
		Map<String, Object> props = KafkaTestUtils.consumerProps("order-completed-test-group", "true", embeddedKafkaBroker);
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		consumer = new KafkaConsumer<>(props);
		embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.ORDER_COMPLETED);
	}

	@AfterEach
	void tearDown() {
		consumer.close();
	}

	@Test
	void create_publishesEventToKafka_afterCommit_whenOrderSucceeds() {
		Member member = memberRepository.save(new Member("카프카테스트-성공"));
		Point point = new Point(member.getId());
		point.charge(10000L);
		pointRepository.save(point);
		Menu menu = menuRepository.save(new Menu("아메리카노", 4500));

		OrderCreateResponse response = orderService.create(member.getId(), menu.getId(), 1, null);

		ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
		boolean found = false;
		for (ConsumerRecord<String, String> record : records) {
			if (record.value().contains(response.orderGroupId())) {
				found = true;
			}
		}
		assertThat(found).as("커밋된 주문의 orderGroupId를 담은 이벤트가 발행돼야 한다").isTrue();
	}

	@Test
	void create_doesNotPublishEventToKafka_whenOrderRollsBackDueToInsufficientPoint() {
		Member member = memberRepository.save(new Member("카프카테스트-실패"));
		pointRepository.save(new Point(member.getId()));
		Menu menu = menuRepository.save(new Menu("아메리카노", 4500));

		assertThatThrownBy(() -> orderService.create(member.getId(), menu.getId(), 1, null))
				.isInstanceOf(CoffeeOrderException.class);

		ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(3));
		assertThat(records.count()).isZero();
	}
}
