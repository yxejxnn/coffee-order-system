package com.coffeeorder.domain.collector.client;

import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CollectorClientTest {

	private static final String BASE_URL = "http://mock-collector";
	private static final String ENDPOINT = BASE_URL + "/mock/collector/orders";

	private MockRestServiceServer server;
	private CollectorClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new CollectorClient(builder.build());
	}

	@Test
	void send_callsOnce_whenFirstAttemptSucceeds() {
		server.expect(times(1), requestTo(ENDPOINT))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess());

		client.send(new OrderCompletedEvent("group-1", 1L, 2L, 4500L));

		server.verify();
	}

	@Test
	void send_doesNotThrow_afterExhaustingRetriesOnRepeatedFailure() {
		server.expect(times(3), requestTo(ENDPOINT))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withServerError());

		client.send(new OrderCompletedEvent("group-2", 1L, 2L, 4500L));

		server.verify();
	}
}
