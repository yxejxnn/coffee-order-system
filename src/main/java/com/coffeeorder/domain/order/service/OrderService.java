package com.coffeeorder.domain.order.service;

import com.coffeeorder.common.exception.CoffeeOrderException;
import com.coffeeorder.common.exception.ErrorCode;
import com.coffeeorder.domain.member.repository.MemberRepository;
import com.coffeeorder.domain.menu.entity.Menu;
import com.coffeeorder.domain.menu.repository.MenuRepository;
import com.coffeeorder.domain.order.dto.OrderCreateResponse;
import com.coffeeorder.domain.order.entity.Order;
import com.coffeeorder.domain.order.event.OrderCompletedEvent;
import com.coffeeorder.domain.order.repository.OrderRepository;
import com.coffeeorder.domain.point.entity.Point;
import com.coffeeorder.domain.point.service.PointService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

	private final OrderRepository orderRepository;
	private final MemberRepository memberRepository;
	private final MenuRepository menuRepository;
	private final PointService pointService;
	private final ApplicationEventPublisher eventPublisher;

	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

	@Transactional
	public OrderCreateResponse create(Long memberId, Long menuId, Integer quantity, String idempotencyKey) {
		// Idempotency-Key(선택)는 "이미 처리된 재시도인가"를 가장 먼저 확인한다 — quantity 등 이번 요청의
		// body 검증보다도 앞서야, 재시도 body가 우연히/실수로 잘못돼도(예: quantity 0) 첫 응답을 그대로
		// 돌려주는 멱등 보장이 깨지지 않는다(자체 리뷰에서 발견).
		if (idempotencyKey != null) {
			if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
				throw new CoffeeOrderException(ErrorCode.INVALID_INPUT);
			}
			Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
			if (existing.isPresent()) {
				Order order = existing.get();
				// 그 키로 저장된 주문이 이번 요청과 다른 회원 것이면 반환하지 않는다 — 그대로 돌려주면 키를
				// 알아내거나 재사용한 제3자에게 남의 주문 내역·현재 잔액이 그대로 노출된다(자체 리뷰에서 발견).
				if (!order.getMemberId().equals(memberId)) {
					throw new CoffeeOrderException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
				}
				return OrderCreateResponse.from(order, pointService.getBalance(order.getMemberId()));
			}
		}

		int orderQuantity = (quantity != null) ? quantity : 1;
		if (orderQuantity <= 0) {
			throw new CoffeeOrderException(ErrorCode.INVALID_QUANTITY);
		}

		if (!memberRepository.existsById(memberId)) {
			throw new CoffeeOrderException(ErrorCode.MEMBER_NOT_FOUND);
		}
		Menu menu = menuRepository.findById(menuId)
				.orElseThrow(() -> new CoffeeOrderException(ErrorCode.MENU_NOT_FOUND));

		Long totalPrice = (long) menu.getPrice() * orderQuantity;
		String orderGroupId = UUID.randomUUID().toString();

		Point point = pointService.use(memberId, totalPrice, orderGroupId);

		Order order = new Order(memberId, menuId, orderQuantity, menu.getPrice(), totalPrice, orderGroupId, idempotencyKey);
		Order savedOrder = orderRepository.save(order);

		eventPublisher.publishEvent(new OrderCompletedEvent(orderGroupId, memberId, menuId, totalPrice));

		return OrderCreateResponse.from(savedOrder, point.getBalance());
	}
}
