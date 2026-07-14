package com.coffeeorder.common.exception;

public class CoffeeOrderException extends RuntimeException {

	private final ErrorCode errorCode;

	public CoffeeOrderException(ErrorCode errorCode) {
		super(errorCode.getDefaultMessage());
		this.errorCode = errorCode;
	}

	public CoffeeOrderException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
