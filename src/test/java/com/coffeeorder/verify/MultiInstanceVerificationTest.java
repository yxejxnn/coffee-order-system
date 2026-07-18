package com.coffeeorder.verify;

import static org.assertj.core.api.Assertions.assertThat;

import com.coffeeorder.domain.ranking.RankingRedisKeys;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 이슈 #9 — 이미 떠 있는 두 개의 앱 인스턴스(같은 MySQL/Redis 공유)를 실제 HTTP로 두들겨
 * 동시성·데이터 일관성을 외부에서 관찰하는 검증. 같은 JVM 안에서 서비스 메서드를 직접
 * 호출하는 {@code *ConcurrencyTest}들과 달리, 서로 다른 프로세스 두 개가 실제로 존재한다는
 * 전제를 검증하므로 기본 {@code ./gradlew test}에서 제외하고 {@code verifyMultiInstance}
 * 태스크로만 실행한다({@code scripts/verify-multi-instance.sh} 참고).
 */
@Tag("multi-instance")
class MultiInstanceVerificationTest {

	private static final int CHARGE_THREAD_COUNT = 20;
	private static final long CHARGE_AMOUNT = 1000L;
	private static final int ORDER_THREAD_COUNT = 15;
	private static final int AFFORDABLE_ORDERS = 5;
	private static final Duration READY_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration RANKING_POLL_TIMEOUT = Duration.ofSeconds(10);

	private static HttpClient httpClient;
	private static Connection dbConnection;
	private static RedisClient redisClient;
	private static StatefulRedisConnection<String, String> redisConnection;

	private static int port1;
	private static int port2;
	private static Long chargeMemberId;
	private static Long orderMemberId;
	private static Long menuId;
	private static long menuPrice;

	@BeforeAll
	static void setUp() throws Exception {
		port1 = Integer.parseInt(System.getProperty("instance1.port", "8080"));
		port2 = Integer.parseInt(System.getProperty("instance2.port", "8081"));
		httpClient = HttpClient.newHttpClient();
		dbConnection = DriverManager.getConnection(jdbcUrl(), env("DB_USERNAME", null), env("DB_PASSWORD", null));
		redisClient = RedisClient.create("redis://" + env("REDIS_HOST", "localhost") + ":" + env("REDIS_PORT", "6379"));
		redisConnection = redisClient.connect();

		waitUntilReady();

		List<Long> memberIds = readMemberIds();
		assertThat(memberIds)
			.as("시드 회원이 2명 이상 있어야 한다 (DataSeeder가 앱 기동 시 심음)")
			.hasSizeGreaterThanOrEqualTo(2);
		chargeMemberId = memberIds.get(0);
		orderMemberId = memberIds.get(1);

		try (PreparedStatement statement = dbConnection.prepareStatement("SELECT id, price FROM menus ORDER BY id LIMIT 1");
			 ResultSet resultSet = statement.executeQuery()) {
			assertThat(resultSet.next()).as("시드 메뉴가 있어야 한다").isTrue();
			menuId = resultSet.getLong("id");
			menuPrice = resultSet.getLong("price");
		}
	}

	@AfterAll
	static void tearDown() throws Exception {
		if (redisConnection != null) {
			redisConnection.close();
		}
		if (redisClient != null) {
			redisClient.shutdown();
		}
		if (dbConnection != null) {
			dbConnection.close();
		}
	}

	@Test
	void charge_concurrentRequestsAcrossTwoInstances_noLostUpdate() throws Exception {
		long before = readPointBalance(chargeMemberId);

		ExecutorService executor = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(CHARGE_THREAD_COUNT);
		AtomicInteger successCount = new AtomicInteger();
		for (int i = 0; i < CHARGE_THREAD_COUNT; i++) {
			int port = (i % 2 == 0) ? port1 : port2;
			executor.submit(() -> {
				try {
					int status = postJson(port, "/api/points/charge",
						"{\"memberId\":%d,\"amount\":%d}".formatted(chargeMemberId, CHARGE_AMOUNT));
					if (status == 200) {
						successCount.incrementAndGet();
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					latch.countDown();
				}
			});
		}
		boolean completedInTime = latch.await(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(completedInTime).as("모든 충전 요청이 타임아웃 없이 끝나야 한다").isTrue();
		assertThat(successCount.get()).isEqualTo(CHARGE_THREAD_COUNT);

		long after = readPointBalance(chargeMemberId);
		assertThat(after - before).isEqualTo(CHARGE_THREAD_COUNT * CHARGE_AMOUNT);
	}

	@Test
	void order_concurrentRequestsAcrossTwoInstances_noOversellAndAccurateRanking() throws Exception {
		int chargeStatus = postJson(port1, "/api/points/charge",
			"{\"memberId\":%d,\"amount\":%d}".formatted(orderMemberId, menuPrice * AFFORDABLE_ORDERS));
		assertThat(chargeStatus).as("사전 충전이 성공해야 한다").isEqualTo(200);

		long balanceBefore = readPointBalance(orderMemberId);
		long orderCountBefore = readOrderCount(orderMemberId);
		double rankingScoreBefore = readRankingScore(menuId);

		ExecutorService executor = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(ORDER_THREAD_COUNT);
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger insufficientCount = new AtomicInteger();
		for (int i = 0; i < ORDER_THREAD_COUNT; i++) {
			int port = (i % 2 == 0) ? port1 : port2;
			executor.submit(() -> {
				try {
					int status = postJson(port, "/api/orders",
						"{\"memberId\":%d,\"menuId\":%d,\"quantity\":1}".formatted(orderMemberId, menuId));
					if (status == 201) {
						successCount.incrementAndGet();
					} else if (status == 409) {
						insufficientCount.incrementAndGet();
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					latch.countDown();
				}
			});
		}
		boolean completedInTime = latch.await(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(completedInTime).as("모든 주문 요청이 타임아웃 없이 끝나야 한다").isTrue();

		assertThat(successCount.get()).isEqualTo(AFFORDABLE_ORDERS);
		assertThat(insufficientCount.get()).isEqualTo(ORDER_THREAD_COUNT - AFFORDABLE_ORDERS);

		long balanceAfter = readPointBalance(orderMemberId);
		assertThat(balanceAfter - balanceBefore).as("정확히 소진되어야 한다(초과차감 없음)").isZero();

		long orderCountAfter = readOrderCount(orderMemberId);
		assertThat(orderCountAfter - orderCountBefore).isEqualTo(AFFORDABLE_ORDERS);

		/* Kafka 컨슈머 랙만큼 랭킹 반영이 늦을 수 있어 목표치에 도달할 때까지 짧게 폴링한다. */
		double rankingScoreAfter = pollRankingScoreUntil(menuId, rankingScoreBefore + AFFORDABLE_ORDERS);
		assertThat(rankingScoreAfter - rankingScoreBefore).isEqualTo((double) AFFORDABLE_ORDERS);
	}

	private static void waitUntilReady() throws InterruptedException {
		long deadline = System.currentTimeMillis() + READY_TIMEOUT.toMillis();
		while (System.currentTimeMillis() < deadline) {
			if (isReady(port1) && isReady(port2) && readMemberIds().size() >= 2) {
				return;
			}
			Thread.sleep(500);
		}
		throw new IllegalStateException(
			"두 인스턴스가 준비되지 않았다. scripts/verify-multi-instance.sh로 SERVER_PORT=" + port1 + "/" + port2
				+ " 두 인스턴스를 먼저 기동했는지 확인하라.");
	}

	private static boolean isReady(int port) {
		try {
			return httpGet(port, "/api/menus") == 200;
		} catch (Exception e) {
			return false;
		}
	}

	private static int httpGet(int port, String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.GET()
			.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private static int postJson(int port, String path, String body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private static List<Long> readMemberIds() {
		List<Long> ids = new ArrayList<>();
		try (PreparedStatement statement = dbConnection.prepareStatement("SELECT id FROM members ORDER BY id LIMIT 2");
			 ResultSet resultSet = statement.executeQuery()) {
			while (resultSet.next()) {
				ids.add(resultSet.getLong("id"));
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return ids;
	}

	private static long readPointBalance(Long memberId) throws Exception {
		try (PreparedStatement statement = dbConnection.prepareStatement("SELECT balance FROM points WHERE member_id = ?")) {
			statement.setLong(1, memberId);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getLong("balance");
			}
		}
	}

	private static long readOrderCount(Long memberId) throws Exception {
		try (PreparedStatement statement = dbConnection.prepareStatement("SELECT COUNT(*) FROM orders WHERE member_id = ?")) {
			statement.setLong(1, memberId);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getLong(1);
			}
		}
	}

	private static double readRankingScore(Long menuId) {
		RedisCommands<String, String> commands = redisConnection.sync();
		String key = RankingRedisKeys.rankingKey(LocalDate.now(RankingRedisKeys.RANKING_ZONE));
		Double score = commands.zscore(key, menuId.toString());
		return score == null ? 0.0 : score;
	}

	private static double pollRankingScoreUntil(Long menuId, double target) throws InterruptedException {
		long deadline = System.currentTimeMillis() + RANKING_POLL_TIMEOUT.toMillis();
		double score = readRankingScore(menuId);
		while (score < target && System.currentTimeMillis() < deadline) {
			Thread.sleep(300);
			score = readRankingScore(menuId);
		}
		return score;
	}

	private static String jdbcUrl() {
		return "jdbc:mysql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "3306")
			+ "/" + env("DB_NAME", "coffee_order") + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8";
	}

	private static String env(String name, String defaultValue) {
		String value = System.getenv(name);
		if (value != null) {
			return value;
		}
		if (defaultValue == null) {
			throw new IllegalStateException(name + " 환경변수가 필요하다(앱과 동일 크리덴셜).");
		}
		return defaultValue;
	}
}
