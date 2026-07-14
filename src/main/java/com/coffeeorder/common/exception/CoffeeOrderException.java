package com.coffeeorder.common.exception;

import lombok.Getter;

@Getter
public class CoffeeOrderException extends RuntimeException {

	private final ErrorCode errorCode;

	public CoffeeOrderException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public CoffeeOrderException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
}
