package com.coffeeorder.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coffeeorder.common.exception.CoffeeOrderException;
import com.coffeeorder.domain.member.entity.Member;
import com.coffeeorder.domain.member.repository.MemberRepository;
import com.coffeeorder.domain.menu.entity.Menu;
import com.coffeeorder.domain.menu.repository.MenuRepository;
import com.coffeeorder.domain.order.entity.Order;
import com.coffeeorder.domain.order.repository.OrderRepository;
import com.coffeeorder.domain.point.entity.Point;
import com.coffeeorder.domain.point.repository.PointRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderServiceConcurrencyTest {

	private static final int THREAD_COUNT = 15;
	private static final int AFFORDABLE_ORDERS = 5;
	private static final int MENU_PRICE = 4500;

	@Autowired
	private OrderService orderService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private MenuRepository menuRepository;

	@Autowired
	private PointRepository pointRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Test
	void create_concurrentOrdersOnSameMember_noOversellAndExactOrderCount() throws InterruptedException {
		Member member = memberRepository.save(new Member("주문동시성테스트"));
		Menu menu = menuRepository.save(new Menu("아메리카노", MENU_PRICE));
		Point point = new Point(member.getId());
		point.charge((long) MENU_PRICE * AFFORDABLE_ORDERS);
		Long pointId = pointRepository.save(point).getId();

		ExecutorService executor = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger insufficientCount = new AtomicInteger();
		for (int i = 0; i < THREAD_COUNT; i++) {
			executor.submit(() -> {
				try {
					orderService.create(member.getId(), menu.getId(), 1);
					successCount.incrementAndGet();
				} catch (CoffeeOrderException e) {
					insufficientCount.incrementAndGet();
				} finally {
					latch.countDown();
				}
			});
		}
		boolean completedInTime = latch.await(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(completedInTime).as("모든 주문 요청이 타임아웃 없이 끝나야 한다").isTrue();

		assertThat(successCount.get()).isEqualTo(AFFORDABLE_ORDERS);
		assertThat(insufficientCount.get()).isEqualTo(THREAD_COUNT - AFFORDABLE_ORDERS);

		Point result = pointRepository.findById(pointId).orElseThrow();
		assertThat(result.getBalance()).isZero();

		List<Order> orders = orderRepository.findAll().stream()
				.filter(order -> order.getMemberId().equals(member.getId()))
				.toList();
		assertThat(orders).hasSize(AFFORDABLE_ORDERS);
	}

	@Test
	void create_concurrentFirstOrdersOnMemberWithoutPoint_noDeadlock() throws InterruptedException {
		// Point가 아직 없는 회원(시드 이전 회원 등 불변식 파손 상태)으로 동시 주문 — order/create가 이미 열어둔
		// 트랜잭션 안에서 point/charge#34의 Point 최초 생성 경로를 타므로, 그 데드락 수정이 실제 주문 경로
		// (호출자가 이미 트랜잭션을 갖고 있는 경우)에서도 적용되는지 검증한다.
		Member member = memberRepository.save(new Member("포인트없는주문회원"));
		Menu menu = menuRepository.save(new Menu("아메리카노", MENU_PRICE));

		ExecutorService executor = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
		List<Throwable> unexpectedFailures = Collections.synchronizedList(new ArrayList<>());
		for (int i = 0; i < THREAD_COUNT; i++) {
			executor.submit(() -> {
				try {
					orderService.create(member.getId(), menu.getId(), 1);
				} catch (CoffeeOrderException e) {
					// balance 0이라 전원 INSUFFICIENT_POINT가 기대값 — 데드락 없이 이 예외로 끝나면 정상
				} catch (Throwable e) {
					unexpectedFailures.add(e);
				} finally {
					latch.countDown();
				}
			});
		}
		boolean completedInTime = latch.await(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(completedInTime).as("모든 주문 요청이 타임아웃 없이 끝나야 한다").isTrue();
		assertThat(unexpectedFailures)
				.as("데드락 등 예기치 못한 예외 없이 전부 INSUFFICIENT_POINT로만 끝나야 한다: " + unexpectedFailures)
				.isEmpty();

		List<Point> points = pointRepository.findAll().stream()
				.filter(point -> point.getMemberId().equals(member.getId()))
				.toList();
		assertThat(points).as("Point가 정확히 1개만 생성돼야 한다").hasSize(1);
	}
}
