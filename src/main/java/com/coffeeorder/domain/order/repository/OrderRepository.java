package com.coffeeorder.domain.order.repository;

import com.coffeeorder.domain.order.entity.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

	Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
