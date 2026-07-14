package com.coffeeorder.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
	INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "충전 금액은 0보다 커야 합니다"),
	INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "수량은 0보다 커야 합니다"),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다"),
	MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 메뉴입니다"),
	INSUFFICIENT_POINT(HttpStatus.CONFLICT, "포인트 잔액이 부족합니다"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public String getCode() {
		return name();
	}
}
