package com.coffeeorder.domain.point.service;

import com.coffeeorder.common.exception.CoffeeOrderException;
import com.coffeeorder.common.exception.ErrorCode;
import com.coffeeorder.domain.point.dto.PointChargeResponse;
import com.coffeeorder.domain.point.entity.Point;
import com.coffeeorder.domain.point.entity.PointHistory;
import com.coffeeorder.domain.point.entity.PointHistoryType;
import com.coffeeorder.domain.point.repository.PointHistoryRepository;
import com.coffeeorder.domain.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

	private final PointRepository pointRepository;
	private final PointHistoryRepository pointHistoryRepository;

	@Transactional
	public PointChargeResponse charge(Long memberId, Long amount) {
		if (amount == null || amount <= 0) {
			throw new CoffeeOrderException(ErrorCode.INVALID_AMOUNT);
		}

		// POINT는 MEMBER와 1:1이며 시드에서 항상 함께 생성되므로, 락 조회 실패는 곧 회원 미존재를 뜻한다.
		Point point = pointRepository.findByMemberIdForUpdate(memberId)
				.orElseThrow(() -> new CoffeeOrderException(ErrorCode.MEMBER_NOT_FOUND));

		point.charge(amount);
		pointHistoryRepository.save(new PointHistory(memberId, PointHistoryType.CHARGE, amount, null));

		return PointChargeResponse.from(point);
	}
}
