package com.coffeeorder.domain.point.service;

import com.coffeeorder.domain.point.entity.Point;
import com.coffeeorder.domain.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointBootstrapService {

	private final PointRepository pointRepository;

	// 별도(REQUIRES_NEW) 트랜잭션으로 분리해 이 안에서 insert 시도와 그 실패가 끝나고 커밋(락 반납)까지
	// 마친 뒤에야 PointService의 바깥 트랜잭션이 SELECT ... FOR UPDATE로 그 행을 잠근다. 같은 트랜잭션
	// 안에서 insert 실패(uk_member_id 위반 시 잡히는 공유 락)에 이어 곧바로 FOR UPDATE로 락을 승격하면
	// 동시에 같은 행에 부딪힌 여러 트랜잭션이 서로의 공유 락을 기다리며 데드락이 난다(동시성 테스트로 확인).
	//
	// unique 제약 위반은 여기서 catch하지 않는다 — 이 메서드 안에서 잡아버리면(정상 반환) JPA가 이미
	// EntityManager의 트랜잭션을 rollback-only로 표시해둔 상태라, Spring이 반환 직후 커밋을 시도하다
	// UnexpectedRollbackException을 새로 던져버린다(직접 재현·확인). 예외를 그대로 던지면 Spring이
	// 정상적으로 롤백 후 원래 DataIntegrityViolationException을 호출자에게 전파하므로, 호출자(PointService)가
	// 그걸 잡아 무시하면 된다.
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void ensurePointExists(Long memberId) {
		pointRepository.saveAndFlush(new Point(memberId));
	}
}
