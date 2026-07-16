package com.coffeeorder.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coffeeorder.common.exception.CoffeeOrderException;
import com.coffeeorder.common.exception.ErrorCode;
import com.coffeeorder.domain.member.repository.MemberRepository;
import com.coffeeorder.domain.point.dto.PointChargeResponse;
import com.coffeeorder.domain.point.entity.Point;
import com.coffeeorder.domain.point.entity.PointHistory;
import com.coffeeorder.domain.point.entity.PointHistoryType;
import com.coffeeorder.domain.point.repository.PointHistoryRepository;
import com.coffeeorder.domain.point.repository.PointRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

	@Mock
	private PointRepository pointRepository;

	@Mock
	private PointHistoryRepository pointHistoryRepository;

	@Mock
	private MemberRepository memberRepository;

	@Test
	void charge_increasesBalanceAndRecordsHistory_whenValid() {
		Point point = new Point(1L);
		point.charge(5000L);
		when(pointRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(point));

		PointChargeResponse response = new PointService(pointRepository, pointHistoryRepository, memberRepository).charge(1L, 3000L);

		assertThat(response.memberId()).isEqualTo(1L);
		assertThat(response.balance()).isEqualTo(8000L);
		verify(pointHistoryRepository).save(argThatChargeHistory(1L, 3000L));
	}

	@Test
	void charge_throwsInvalidAmount_whenAmountIsZeroOrNegative() {
		PointService pointService = new PointService(pointRepository, pointHistoryRepository, memberRepository);

		assertThatThrownBy(() -> pointService.charge(1L, 0L))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_AMOUNT);
		assertThatThrownBy(() -> pointService.charge(1L, -1000L))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_AMOUNT);
		verify(pointRepository, never()).findByMemberIdForUpdate(any());
	}

	@Test
	void charge_throwsInvalidAmount_whenAmountIsNull() {
		PointService pointService = new PointService(pointRepository, pointHistoryRepository, memberRepository);

		assertThatThrownBy(() -> pointService.charge(1L, null))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_AMOUNT);
	}

	@Test
	void charge_throwsMemberNotFound_whenPointRowMissingAndMemberDoesNotExist() {
		when(pointRepository.findByMemberIdForUpdate(999L)).thenReturn(Optional.empty());
		when(memberRepository.existsById(999L)).thenReturn(false);
		PointService pointService = new PointService(pointRepository, pointHistoryRepository, memberRepository);

		assertThatThrownBy(() -> pointService.charge(999L, 3000L))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
	}

	@Test
	void charge_createsPointAndSucceeds_whenPointRowMissingButMemberExists() {
		when(pointRepository.findByMemberIdForUpdate(7L)).thenReturn(Optional.empty());
		when(memberRepository.existsById(7L)).thenReturn(true);
		when(pointRepository.save(org.mockito.ArgumentMatchers.any(Point.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		PointService pointService = new PointService(pointRepository, pointHistoryRepository, memberRepository);

		PointChargeResponse response = pointService.charge(7L, 3000L);

		assertThat(response.memberId()).isEqualTo(7L);
		assertThat(response.balance()).isEqualTo(3000L);
	}

	private static PointHistory argThatChargeHistory(Long memberId, Long amount) {
		return org.mockito.ArgumentMatchers.argThat(history ->
				history.getMemberId().equals(memberId)
						&& history.getType() == PointHistoryType.CHARGE
						&& history.getAmount().equals(amount));
	}
}
