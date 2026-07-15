package com.coffeeorder.common.response;

import com.coffeeorder.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

	@Test
	void ok_withData_wrapsCodeAndData() {
		ApiResponse<String> response = ApiResponse.ok("hello");

		assertThat(response.getCode()).isEqualTo("SUCCESS");
		assertThat(response.getData()).isEqualTo("hello");
		assertThat(response.getMessage()).isNull();
	}

	@Test
	void ok_withoutData_hasNullData() {
		ApiResponse<Void> response = ApiResponse.ok();

		assertThat(response.getCode()).isEqualTo("SUCCESS");
		assertThat(response.getData()).isNull();
	}

	@Test
	void error_withCodeAndMessage_wrapsBoth_withNullData() {
		ApiResponse<Void> response = ApiResponse.error("MEMBER_001", "존재하지 않는 회원입니다");

		assertThat(response.getCode()).isEqualTo("MEMBER_001");
		assertThat(response.getMessage()).isEqualTo("존재하지 않는 회원입니다");
		assertThat(response.getData()).isNull();
	}

	@Test
	void error_withErrorCode_usesErrorCodeMessage() {
		ApiResponse<Void> response = ApiResponse.error(ErrorCode.MEMBER_NOT_FOUND);

		assertThat(response.getCode()).isEqualTo("MEMBER_001");
		assertThat(response.getMessage()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getMessage());
	}

	@Test
	void error_withErrorCodeAndCustomMessage_overridesMessage() {
		ApiResponse<Void> response = ApiResponse.error(ErrorCode.MEMBER_NOT_FOUND, "커스텀 메시지");

		assertThat(response.getCode()).isEqualTo("MEMBER_001");
		assertThat(response.getMessage()).isEqualTo("커스텀 메시지");
	}
}
