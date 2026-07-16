package com.coffeeorder.domain.collector.dto;

import com.coffeeorder.domain.order.event.OrderCompletedEvent;

/**
 * 데이터 수집 플랫폼(Mock)으로 보내는 전송 페이로드.
 *
 * <p>발제 요구사항이 명시한 "회원 식별값·메뉴·결제금액"만 담는다({@code orderGroupId}는 우리
 * 내부 식별자라 외부 플랫폼에는 넘기지 않는다). {@link com.coffeeorder.domain.collector.client.CollectorClient}가
 * 이 레코드를 JSON 바디로 직렬화해 {@code POST}로 전송하고,
 * {@link com.coffeeorder.domain.collector.mock.MockCollectorController}가 같은 타입으로 역직렬화해 받는다
 * (Mock이라 요청·응답 양쪽에서 재사용).
 *
 * @param memberId 주문한 회원 식별값
 * @param menuId   주문한 메뉴 식별값
 * @param amount   결제금액({@link OrderCompletedEvent#totalPrice()})
 */
public record CollectorTransmitRequest(
	Long memberId,
	Long menuId,
	Long amount
) {

	/**
	 * 주문 완료 이벤트로부터 전송 페이로드를 만든다.
	 *
	 * @param event 주문 완료 이벤트
	 * @return 변환된 {@link CollectorTransmitRequest}
	 */
	public static CollectorTransmitRequest from(OrderCompletedEvent event) {
		return new CollectorTransmitRequest(
			event.memberId(),
			event.menuId(),
			event.totalPrice());
	}
}
