package com.coffeeorder.domain.order.entity;

import com.coffeeorder.domain.member.entity.Member;
import com.coffeeorder.domain.menu.entity.Menu;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
		name = "orders",
		uniqueConstraints = @UniqueConstraint(name = "uk_order_group_id", columnNames = "order_group_id"),
		indexes = @Index(name = "idx_created_menu", columnList = "created_at, menu_id")
)
@Getter
public class Orders {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "menu_id", nullable = false)
	private Menu menu;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "unit_price", nullable = false)
	private Integer unitPrice;

	@Column(name = "total_price", nullable = false)
	private Long totalPrice;

	@Column(name = "order_group_id", nullable = false, length = 36)
	private String orderGroupId;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Orders() {
	}

	public Orders(Member member, Menu menu, Integer quantity, Integer unitPrice, Long totalPrice, String orderGroupId) {
		this.member = member;
		this.menu = menu;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.totalPrice = totalPrice;
		this.orderGroupId = orderGroupId;
	}
}
