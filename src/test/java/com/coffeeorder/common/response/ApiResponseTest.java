package com.coffeeorder.common.response;

import com.coffeeorder.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

	@Test
	void ok_withData_wrapsCodeAndData() {
		ApiResponse<String> response = ApiResponse.ok("hello");

		assertThat(response.code()).isEqualTo("SUCCESS");
		assertThat(response.data()).isEqualTo("hello");
		assertThat(response.message()).isNull();
	}

	@Test
	void ok_withoutData_hasNullData() {
		ApiResponse<Void> response = ApiResponse.ok();

		assertThat(response.code()).isEqualTo("SUCCESS");
		assertThat(response.data()).isNull();
	}

	@Test
	void error_withErrorCode_usesErrorCodeMessage() {
		ApiResponse<Void> response = ApiResponse.error(ErrorCode.MEMBER_NOT_FOUND);

		assertThat(response.code()).isEqualTo("MEMBER_001");
		assertThat(response.message()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getMessage());
	}

	@Test
	void error_withErrorCodeAndCustomMessage_overridesMessage() {
		ApiResponse<Void> response = ApiResponse.error(ErrorCode.MEMBER_NOT_FOUND, "커스텀 메시지");

		assertThat(response.code()).isEqualTo("MEMBER_001");
		assertThat(response.message()).isEqualTo("커스텀 메시지");
	}
}
