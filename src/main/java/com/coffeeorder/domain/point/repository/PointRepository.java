package com.coffeeorder.domain.point.repository;

import com.coffeeorder.domain.point.entity.Point;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointRepository extends JpaRepository<Point, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Point p WHERE p.memberId = :memberId")
	Optional<Point> findByMemberIdForUpdate(@Param("memberId") Long memberId);

	// 잠금 없는 일반 조회(MVCC 스냅샷) — 존재 여부만 확인할 때는 이걸 먼저 써서, 존재하지 않는 행에
	// SELECT ... FOR UPDATE(갭 락 위험)를 걸지 않는다.
	boolean existsByMemberId(Long memberId);
}
