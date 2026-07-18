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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

	private final PointRepository pointRepository;
	private final PointHistoryRepository pointHistoryRepository;
	private final MemberRepository memberRepository;
	private final PointBootstrapService pointBootstrapService;

	// isolation = READ_COMMITTED: MySQL 기본(REPEATABLE READ)은 존재하지 않는 행에 대한
	// `SELECT ... FOR UPDATE`(getLockedPoint의 최초 락 조회)에 갭 락을 건다. Point가 아직 없는
	// 같은 회원에게 여러 트랜잭션이 동시에 첫 충전을 요청하면 이 갭 락들이 서로 얽혀 데드락이 난다
	// (동시성 테스트로 직접 재현). READ COMMITTED는 갭 락을 쓰지 않아 이 데드락이 사라지고,
	// 이미 존재하는 행에 대한 락 동작(ADR-001의 핵심 시나리오)은 그대로 유지된다.
	@Transactional(isolation = Isolation.READ_COMMITTED)
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

	@Transactional(isolation = Isolation.READ_COMMITTED)
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
	// 생성은 별도 트랜잭션(PointBootstrapService, REQUIRES_NEW)에 위임해 그 트랜잭션이 커밋(락 반납)까지
	// 끝낸 뒤에야 이 트랜잭션이 새로 SELECT ... FOR UPDATE로 락을 건다(이유는 PointBootstrapService 참고).
	// unique 제약 위반(동시에 다른 트랜잭션이 먼저 만듦)은 ensurePointExists 쪽 트랜잭션이 이미 롤백을
	// 마친 뒤 DataIntegrityViolationException으로 여기까지 전파되므로, 여기서 잡아 무시하고 재조회로 이어간다.
	private Point createPointForExistingMember(Long memberId) {
		if (!memberRepository.existsById(memberId)) {
			throw new CoffeeOrderException(ErrorCode.MEMBER_NOT_FOUND);
		}
		try {
			pointBootstrapService.ensurePointExists(memberId);
		} catch (DataIntegrityViolationException e) {
			// 이미 존재 — 무시하고 아래에서 재조회
		}
		return pointRepository.findByMemberIdForUpdate(memberId)
				.orElseThrow(() -> new IllegalStateException("ensurePointExists 직후 Point를 찾을 수 없음: memberId=" + memberId));
	}
}
