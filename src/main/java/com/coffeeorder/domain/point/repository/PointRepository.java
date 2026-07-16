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
}
