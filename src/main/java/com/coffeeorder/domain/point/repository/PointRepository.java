package com.coffeeorder.domain.point.repository;

import com.coffeeorder.domain.point.entity.Point;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRepository extends JpaRepository<Point, Long> {
}
