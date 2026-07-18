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

	@Transactional
	public OrderCreateResponse create(Long memberId, Long menuId, Integer quantity) {
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

		Order order = new Order(memberId, menuId, orderQuantity, menu.getPrice(), totalPrice, orderGroupId);
		Order savedOrder = orderRepository.save(order);

		eventPublisher.publishEvent(new OrderCompletedEvent(orderGroupId, memberId, menuId, totalPrice));

		return OrderCreateResponse.from(savedOrder, point.getBalance());
	}
}
