package com.coffeeorder.common.response;

import lombok.Getter;

@Getter
public final class ApiResponse<T> {

	private final String code;
	private final String message;
	private final T data;

	private ApiResponse(String code, String message, T data) {
		this.code = code;
		this.message = message;
		this.data = data;
	}

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>("SUCCESS", null, data);
	}

	public static ApiResponse<Void> success() {
		return new ApiResponse<>("SUCCESS", null, null);
	}

	public static <T> ApiResponse<T> error(String code, String message) {
		return new ApiResponse<>(code, message, null);
	}
}
