package com.coffeeorder.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
		name = "orders",
		uniqueConstraints = {
			@UniqueConstraint(name = "uk_order_group_id", columnNames = "order_group_id"),
			@UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
		},
		indexes = @Index(name = "idx_created_menu", columnList = "created_at, menu_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "menu_id", nullable = false)
	private Long menuId;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "unit_price", nullable = false)
	private Integer unitPrice;

	@Column(name = "total_price", nullable = false)
	private Long totalPrice;

	@Column(name = "order_group_id", nullable = false, length = 36)
	private String orderGroupId;

	// 클라이언트가 보낸 Idempotency-Key(선택) — 재시도 중복 차단용. 헤더를 안 보내면 null(유니크 제약은
	// MySQL에서 NULL을 여러 개 허용하므로 기존 흐름엔 영향 없음).
	@Column(name = "idempotency_key", length = 100)
	private String idempotencyKey;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public Order(Long memberId, Long menuId, Integer quantity, Integer unitPrice, Long totalPrice, String orderGroupId,
			String idempotencyKey) {
		this.memberId = memberId;
		this.menuId = menuId;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.totalPrice = totalPrice;
		this.orderGroupId = orderGroupId;
		this.idempotencyKey = idempotencyKey;
	}
}
