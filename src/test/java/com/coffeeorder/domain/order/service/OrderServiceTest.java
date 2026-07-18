package com.coffeeorder.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private MenuRepository menuRepository;

	@Mock
	private PointService pointService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Test
	void create_savesOrderAndPublishesEvent_whenValid() {
		when(memberRepository.existsById(1L)).thenReturn(true);
		when(menuRepository.findById(2L)).thenReturn(Optional.of(new Menu("아메리카노", 4500)));
		Point point = new Point(1L);
		point.charge(10000L);
		when(pointService.use(eq(1L), eq(4500L), anyString())).thenReturn(point);
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
		OrderService orderService = new OrderService(orderRepository, memberRepository, menuRepository, pointService, eventPublisher);

		OrderCreateResponse response = orderService.create(1L, 2L, 1, null);

		assertThat(response.memberId()).isEqualTo(1L);
		assertThat(response.menuId()).isEqualTo(2L);
		assertThat(response.quantity()).isEqualTo(1);
		assertThat(response.totalPrice()).isEqualTo(4500L);
		assertThat(response.balance()).isEqualTo(10000L);
		assertThat(response.orderGroupId()).isNotBlank();

		ArgumentCaptor<OrderCompletedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCompletedEvent.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		OrderCompletedEvent publishedEvent = eventCaptor.getValue();
		assertThat(publishedEvent.orderGroupId()).isEqualTo(response.orderGroupId());
		assertThat(publishedEvent.memberId()).isEqualTo(1L);
		assertThat(publishedEvent.menuId()).isEqualTo(2L);
		assertThat(publishedEvent.totalPrice()).isEqualTo(4500L);
	}

	@Test
	void create_defaultsQuantityToOne_whenQuantityIsNull() {
		when(memberRepository.existsById(1L)).thenReturn(true);
		when(menuRepository.findById(2L)).thenReturn(Optional.of(new Menu("아메리카노", 4500)));
		Point point = new Point(1L);
		point.charge(10000L);
		when(pointService.use(eq(1L), eq(4500L), anyString())).thenReturn(point);
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
		OrderService orderService = new OrderService(orderRepository, memberRepository, menuRepository, pointService, eventPublisher);

		OrderCreateResponse response = orderService.create(1L, 2L, null, null);

		assertThat(response.quantity()).isEqualTo(1);
		assertThat(response.totalPrice()).isEqualTo(4500L);
	}

	@Test
	void create_throwsInvalidQuantity_whenQuantityIsZeroOrNegative() {
		OrderService orderService = new OrderService(orderRepository, memberRepository, menuRepository, pointService, eventPublisher);

		assertThatThrownBy(() -> orderService.create(1L, 2L, 0, null))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_QUANTITY);
		assertThatThrownBy(() -> orderService.create(1L, 2L, -1, null))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_QUANTITY);
		verify(memberRepository, never()).existsById(any());
		verify(menuRepository, never()).findById(any());
		verify(pointService, never()).use(anyLong(), anyLong(), anyString());
		verify(orderRepository, never()).save(any());
	}

	@Test
	void create_throwsInvalidQuantity_beforeCheckingMenuOrMember_whenBothAreInvalidToo() {
		// menuId도 무효인 상황에서 quantity 오류가 가려지지 않고 먼저 보고돼야 한다(#35)
		OrderService orderService = new OrderService(orderRepository, memberRepository, menuRepository, pointService, eventPublisher);

		assertThatThrownBy(() -> orderService.create(999L, 999L, 0, null))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INVALID_QUANTITY);
		verify(memberRepository, never()).existsById(any());
		verify(menuRepository, never()).findById(any());
	}

	@Test
	void create_throwsMemberNotFound_whenMemberDoesNotExist() {
		when(memberRepository.existsById(999L)).thenReturn(false);
		OrderService orderService = new OrderService(orderRepository, memberRepository, menuRepository, pointService, eventPublisher);

		assertThatThrownBy(() -> orderService.create(999L, 2L, 1, null))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
		verify(menuRepository, never()).findById(any());
	}

	@Test
	void create_throwsMenuNotFound_whenMenuDoesNotExist() {
		when(memberRepository.existsById(1L)).thenReturn(true);
		when(menuRepository.findById(999L)).thenReturn(Optional.empty());
		OrderService orderService = new OrderService(orderRepository, memberRepository, menuRepository, pointService, eventPublisher);

		assertThatThrownBy(() -> orderService.create(1L, 999L, 1, null))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.MENU_NOT_FOUND);
	}

	@Test
	void create_propagatesInsufficientPointAndSkipsOrderSaveAndEvent_whenBalanceInsufficient() {
		when(memberRepository.existsById(1L)).thenReturn(true);
		when(menuRepository.findById(2L)).thenReturn(Optional.of(new Menu("아메리카노", 4500)));
		when(pointService.use(eq(1L), eq(4500L), anyString()))
				.thenThrow(new CoffeeOrderException(ErrorCode.INSUFFICIENT_POINT));
		OrderService orderService = new OrderService(orderRepository, memberRepository, menuRepository, pointService, eventPublisher);

		assertThatThrownBy(() -> orderService.create(1L, 2L, 1, null))
				.isInstanceOf(CoffeeOrderException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.INSUFFICIENT_POINT);
		verify(orderRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void create_returnsExistingOrder_whenIdempotencyKeyAlreadyProcessed() {
		Order existingOrder = new Order(1L, 2L, 1, 4500, 4500L, "existing-group-id", "retry-key-1");
		when(orderRepository.findByIdempotencyKey("retry-key-1")).thenReturn(Optional.of(existingOrder));
		when(pointService.getBalance(1L)).thenReturn(9000L);
		OrderService orderService = new OrderService(orderRepository, memberRepository, menuRepository, pointService, eventPublisher);

		OrderCreateResponse response = orderService.create(1L, 2L, 1, "retry-key-1");

		assertThat(response.orderGroupId()).isEqualTo("existing-group-id");
		assertThat(response.balance()).isEqualTo(9000L);
		verify(memberRepository, never()).existsById(any());
		verify(menuRepository, never()).findById(any());
		verify(pointService, never()).use(anyLong(), anyLong(), anyString());
		verify(orderRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void create_proceedsNormally_whenIdempotencyKeyNotSeenBefore() {
		when(orderRepository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
		when(memberRepository.existsById(1L)).thenReturn(true);
		when(menuRepository.findById(2L)).thenReturn(Optional.of(new Menu("아메리카노", 4500)));
		Point point = new Point(1L);
		point.charge(10000L);
		when(pointService.use(eq(1L), eq(4500L), anyString())).thenReturn(point);
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
		OrderService orderService = new OrderService(orderRepository, memberRepository, menuRepository, pointService, eventPublisher);

		OrderCreateResponse response = orderService.create(1L, 2L, 1, "new-key");

		assertThat(response.balance()).isEqualTo(10000L);
		verify(pointService).use(eq(1L), eq(4500L), anyString());
		verify(orderRepository).save(any(Order.class));
	}
}
