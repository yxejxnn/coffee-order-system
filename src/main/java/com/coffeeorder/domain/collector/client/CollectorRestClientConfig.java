package com.coffeeorder.domain.collector.client;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * {@link CollectorClient}가 데이터 수집 플랫폼(Mock)과 통신할 때 쓰는 {@link RestClient} 빈을 만든다.
 *
 * <p>구성을 별도 {@code @Configuration}으로 분리한 이유는 두 가지다.
 * <ol>
 *   <li>{@code baseUrl}을 주입받으려면 {@code @Value}가 필요한데, {@code CollectorClient}를
 *       {@code @RequiredArgsConstructor}(Lombok)로 유지하면서 그 파라미터에 애노테이션을 붙일 방법이
 *       없다 — 완성된 {@link RestClient}만 생성자 주입받게 하면 이 문제가 사라진다.</li>
 *   <li>테스트에서 {@code MockRestServiceServer}를 쓸 때, "builder 생성 → mock 바인딩 → {@code build()}"
 *       순서가 지켜져야 mock이 실제로 요청을 가로챈다. {@code RestClient} 자체를 주입 대상으로 두면
 *       테스트가 이 순서를 그대로 재현하기 쉬워진다(반대로 {@code CollectorClient} 생성자 안에서
 *       {@code requestFactory}를 다시 설정하면, 이미 mock이 심어둔 팩토리를 덮어써 실제 네트워크로
 *       나가버리는 버그가 있었다).</li>
 * </ol>
 *
 * <p><b>타임아웃</b>: connect 2초·read 3초를 명시적으로 설정한다. 이게 없으면
 * {@code collector-group} 컨슈머는 (기본 설정상) 스레드 1개뿐이라, 수집
 * 플랫폼이 응답 없이 멈추면 그 스레드가 무한정 블로킹되어 토픽 전체 소비가 멈추는 문제가 있었다.
 *
 * @see CollectorClient
 */
@Configuration
public class CollectorRestClientConfig {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

	/**
	 * {@code collector.api.base-url}을 기본 URL로 갖고, connect/read 타임아웃이 설정된
	 * {@link RestClient}를 만든다.
	 *
	 * @param builder Spring Boot가 자동 구성한 {@link RestClient.Builder}(프로토타입 빈)
	 * @param baseUrl {@code collector.api.base-url} 프로퍼티 값 — 기본값은
	 *                {@code http://localhost:${server.port}}(자기 자신, 로컬 데모용)
	 * @return 데이터 수집 플랫폼 전용으로 구성된 {@link RestClient}
	 */
	@Bean
	public RestClient collectorRestClient(RestClient.Builder builder, @Value("${collector.api.base-url}") String baseUrl) {
		HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT);
		return builder.baseUrl(baseUrl)
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
	}
}
