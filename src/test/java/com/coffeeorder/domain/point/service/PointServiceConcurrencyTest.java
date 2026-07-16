package com.coffeeorder.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coffeeorder.domain.member.entity.Member;
import com.coffeeorder.domain.member.repository.MemberRepository;
import com.coffeeorder.domain.point.entity.Point;
import com.coffeeorder.domain.point.entity.PointHistory;
import com.coffeeorder.domain.point.repository.PointHistoryRepository;
import com.coffeeorder.domain.point.repository.PointRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PointServiceConcurrencyTest {

	private static final int THREAD_COUNT = 30;
	private static final long CHARGE_AMOUNT = 1000L;

	@Autowired
	private PointService pointService;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PointRepository pointRepository;

	@Autowired
	private PointHistoryRepository pointHistoryRepository;

	@Test
	void charge_concurrentRequestsOnSameMember_noLostUpdate() throws InterruptedException {
		Member member = memberRepository.save(new Member("동시성테스트"));
		Long pointId = pointRepository.save(new Point(member.getId())).getId();

		ExecutorService executor = Executors.newFixedThreadPool(10);
		CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
		for (int i = 0; i < THREAD_COUNT; i++) {
			executor.submit(() -> {
				try {
					pointService.charge(member.getId(), CHARGE_AMOUNT);
				} finally {
					latch.countDown();
				}
			});
		}
		boolean completedInTime = latch.await(30, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(completedInTime).as("모든 충전 요청이 타임아웃 없이 끝나야 한다").isTrue();

		Point result = pointRepository.findById(pointId).orElseThrow();
		assertThat(result.getBalance()).isEqualTo(THREAD_COUNT * CHARGE_AMOUNT);

		List<PointHistory> histories = pointHistoryRepository.findAll().stream()
				.filter(history -> history.getMemberId().equals(member.getId()))
				.toList();
		assertThat(histories).hasSize(THREAD_COUNT);
	}
}
