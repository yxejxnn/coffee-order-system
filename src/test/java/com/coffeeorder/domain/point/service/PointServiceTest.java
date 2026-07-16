package com.coffeeorder.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coffeeorder.common.exception.CoffeeOrderException;
import com.coffeeorder.common.exception.ErrorCode;
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

	@Test
	void charge_increasesBalanceAndRecordsHistory_whenValid() {
		Point point = new Point(1L);
		point.charge(5000L);
		when(pointRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(point));

		PointChargeResponse response = new PointService(pointRepository, pointHistoryRepository).charge(1L, 3000L);

		assertThat(response.getMemberId()).isEqualTo(1L);
		assertThat(response.getBalance()).isEqualTo(8000L);
		verify(pointHistoryRepository).save(argThatChargeHistory(1L, 3000L));
	}

	@Test
	void charge_throwsInvalidAmount_whenAmountIsZeroOrNegative() {
		PointService pointService = new PointService(pointRepository, pointHistoryRepository);

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
		PointService pointService = new PointService(pointRepository, pointHistoryRepository);

		assertThatThrownBy(() -> pointService.charge(1L, null))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_AMOUNT);
	}

	@Test
	void charge_throwsMemberNotFound_whenPointRowMissing() {
		when(pointRepository.findByMemberIdForUpdate(999L)).thenReturn(Optional.empty());
		PointService pointService = new PointService(pointRepository, pointHistoryRepository);

		assertThatThrownBy(() -> pointService.charge(999L, 3000L))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
	}

	private static PointHistory argThatChargeHistory(Long memberId, Long amount) {
		return org.mockito.ArgumentMatchers.argThat(history ->
				history.getMemberId().equals(memberId)
						&& history.getType() == PointHistoryType.CHARGE
						&& history.getAmount().equals(amount));
	}
}
