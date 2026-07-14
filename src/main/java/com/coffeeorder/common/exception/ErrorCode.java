package com.coffeeorder.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// Common
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "서버 내부 오류가 발생했습니다"),

	// Member
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "존재하지 않는 회원입니다"),

	// Menu
	MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "MENU_001", "존재하지 않는 메뉴입니다"),

	// Point
	INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "POINT_001", "충전 금액은 0보다 커야 합니다"),
	INSUFFICIENT_POINT(HttpStatus.CONFLICT, "POINT_002", "포인트 잔액이 부족합니다"),

	// Order
	INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "ORDER_001", "수량은 0보다 커야 합니다");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
