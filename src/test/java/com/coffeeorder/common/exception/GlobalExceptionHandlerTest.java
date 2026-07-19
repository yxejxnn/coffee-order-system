package com.coffeeorder.common.exception;

import com.coffeeorder.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void handleCoffeeOrderException_mapsToErrorCodeStatusAndBody() {
		CoffeeOrderException exception = new CoffeeOrderException(ErrorCode.MEMBER_NOT_FOUND);

		ResponseEntity<ApiResponse<Void>> response = handler.handleCoffeeOrderException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("MEMBER_001");
		assertThat(response.getBody().message()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getMessage());
	}

	@Test
	void handleNoResourceFoundException_mapsTo404NotInternalError() {
		NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/nonexistent", "/nonexistent");

		ResponseEntity<ApiResponse<Void>> response = handler.handleNoResourceFoundException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("COMMON_003");
	}

	@Test
	void handleHttpMessageNotReadableException_mapsToInvalidInput() {
		HttpMessageNotReadableException exception =
			new HttpMessageNotReadableException("malformed JSON", new MockHttpInputMessage(new byte[0]));

		ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadableException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("COMMON_001");
	}

	@Test
	void handleMethodArgumentTypeMismatchException_mapsToInvalidInput() throws NoSuchMethodException {
		MethodParameter param = new MethodParameter(
			GlobalExceptionHandlerTest.class.getDeclaredMethod("handleMethodArgumentTypeMismatchException_mapsToInvalidInput"), -1);
		MethodArgumentTypeMismatchException exception =
			new MethodArgumentTypeMismatchException("abc", Long.class, "quantity", param, new NumberFormatException());

		ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentTypeMismatchException(exception);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("COMMON_001");
	}

	@Test
	void handleException_fallsBackToInternalError() {
		ResponseEntity<ApiResponse<Void>> response = handler.handleException(new RuntimeException("boom"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("COMMON_002");
	}
}
