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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

	private final PointRepository pointRepository;
	private final PointHistoryRepository pointHistoryRepository;
	private final MemberRepository memberRepository;
	private final PointBootstrapService pointBootstrapService;

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

	// 잠금 없는 단순 잔액 조회 — 이미 결제가 끝난 뒤 참고용으로만 잔액이 필요할 때(예: 멱등 재조회 응답) 쓴다.
	// 결제(charge/use)에는 쓰지 않는다 — 그쪽은 반드시 getLockedPoint의 비관적 락을 거쳐야 한다.
	public Long getBalance(Long memberId) {
		return pointRepository.findByMemberId(memberId)
				.orElseThrow(() -> new IllegalStateException("Point가 존재해야 하는 회원인데 없음: memberId=" + memberId))
				.getBalance();
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

	// existsByMemberId(잠금 없는 조회)로 먼저 존재를 확인해, 없을 때만 ensurePointCreated로 만든다.
	// 이렇게 하면 이 트랜잭션이 실제로 SELECT ... FOR UPDATE를 거는 시점엔 그 행이 항상 이미 존재해서
	// (직접 있었거나, 방금 만들어졌거나) 존재하지 않는 행에 대한 갭 락이 애초에 걸릴 일이 없다.
	// (이 트랜잭션의 격리수준·전파 방식에 좌우되지 않는다 — order/create(#5)처럼 이미 열린 바깥
	// 트랜잭션에 REQUIRED로 참여(join)하는 호출자에서도 그대로 안전하다.)
	private Point getLockedPoint(Long memberId) {
		if (!pointRepository.existsByMemberId(memberId)) {
			ensurePointCreated(memberId);
		}
		return pointRepository.findByMemberIdForUpdate(memberId)
				.orElseThrow(() -> new IllegalStateException("Point 생성 후에도 조회되지 않음: memberId=" + memberId));
	}

	// POINT는 시드에서 MEMBER와 함께 생성되지만, 시드 이전에 만들어진 회원 등 그 불변식이 깨진 경우를 대비해
	// 여기서 회원 존재를 별도로 확인하고 없으면 그 자리에서 만든다(회원 존재는 확정된 상태).
	// 생성은 별도 트랜잭션(PointBootstrapService, REQUIRES_NEW)에 위임한다 — 같은 트랜잭션 안에서
	// insert 실패(uk_member_id 위반 시 잡히는 공유 락)에 이어 곧바로 FOR UPDATE로 락을 승격하면, 동시에
	// 같은 행에 부딪힌 여러 트랜잭션이 서로의 공유 락을 기다리며 데드락이 나기 때문(동시성 테스트로 확인).
	// 그 트랜잭션이 커밋/롤백까지 끝난 뒤에야 getLockedPoint가 새로 SELECT ... FOR UPDATE를 건다.
	// unique 제약 위반(동시에 다른 트랜잭션이 먼저 만듦)은 ensurePointExists 쪽 트랜잭션이 이미 롤백을
	// 마친 뒤 예외로 여기까지 전파되므로, 여기서 잡아 무시한다 — 대부분 DataIntegrityViolationException으로
	// 곧바로 실패하지만, 커밋 타이밍이 겹쳐 락 대기가 길어지면 CannotAcquireLockException(락 대기 초과)으로
	// 대신 나타날 수 있어 함께 잡는다.
	private void ensurePointCreated(Long memberId) {
		if (!memberRepository.existsById(memberId)) {
			throw new CoffeeOrderException(ErrorCode.MEMBER_NOT_FOUND);
		}
		try {
			pointBootstrapService.ensurePointExists(memberId);
		} catch (DataIntegrityViolationException | CannotAcquireLockException e) {
			// 이미 존재 — 무시하고 getLockedPoint의 재조회로 이어간다
		}
	}
}
