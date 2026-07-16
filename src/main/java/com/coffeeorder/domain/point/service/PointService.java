package com.coffeeorder.domain.point.service;

import com.coffeeorder.common.exception.CoffeeOrderException;
import com.coffeeorder.common.exception.ErrorCode;
import com.coffeeorder.domain.member.repository.MemberRepository;
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
	private final MemberRepository memberRepository;

	@Transactional
	public PointChargeResponse charge(Long memberId, Long amount) {
		if (amount == null || amount <= 0) {
			throw new CoffeeOrderException(ErrorCode.INVALID_AMOUNT);
		}

		Point point = getLockedPoint(memberId);

		point.charge(amount);
		PointHistory history = new PointHistory(memberId, PointHistoryType.CHARGE, amount, null);
		pointHistoryRepository.save(history);

		return PointChargeResponse.from(point);
	}

	@Transactional
	public Point use(Long memberId, Long amount, String orderGroupId) {
		if (amount == null || amount <= 0) {
			throw new CoffeeOrderException(ErrorCode.INVALID_AMOUNT);
		}

		Point point = getLockedPoint(memberId);

		if (point.getBalance() < amount) {
			throw new CoffeeOrderException(ErrorCode.INSUFFICIENT_POINT);
		}

		point.use(amount);
		PointHistory history = new PointHistory(memberId, PointHistoryType.USE, amount, orderGroupId);
		pointHistoryRepository.save(history);

		return point;
	}

	private Point getLockedPoint(Long memberId) {
		return pointRepository.findByMemberIdForUpdate(memberId)
				.orElseGet(() -> createPointForExistingMember(memberId));
	}

	// POINT는 시드에서 MEMBER와 함께 생성되지만, 시드 이전에 만들어진 회원 등 그 불변식이 깨진 경우를 대비해
	// 락 조회가 비었을 때 회원 존재를 별도로 확인하고 없으면 그 자리에서 만든다(회원 존재는 확정된 상태).
	private Point createPointForExistingMember(Long memberId) {
		if (!memberRepository.existsById(memberId)) {
			throw new CoffeeOrderException(ErrorCode.MEMBER_NOT_FOUND);
		}
		Point newPoint = new Point(memberId);
		return pointRepository.save(newPoint);
	}
}
