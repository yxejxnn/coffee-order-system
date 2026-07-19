package com.coffeeorder.common.response;

import com.coffeeorder.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
		String code,
		String message,
		T data
) {

	private static final String SUCCESS_CODE = "SUCCESS";

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(SUCCESS_CODE, null, data);
	}

	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(SUCCESS_CODE, null, null);
	}

	public static ApiResponse<Void> error(ErrorCode errorCode) {
		return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
	}

	public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
		return new ApiResponse<>(errorCode.getCode(), message, null);
	}
}
