package com.coffeeorder.common.exception;

import com.coffeeorder.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CoffeeOrderException.class)
	public ResponseEntity<ApiResponse<Void>> handleCoffeeOrderException(CoffeeOrderException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode, e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(error -> error.getField() + " " + error.getDefaultMessage())
			.orElse(ErrorCode.INVALID_INPUT.getMessage());
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
			.body(ApiResponse.error(ErrorCode.INVALID_INPUT, message));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
		return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus())
			.body(ApiResponse.error(ErrorCode.NOT_FOUND));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("처리되지 않은 예외 발생", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
			.body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
	}
}
