package com.coffeeorder.common.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

	@Test
	void success_withData_wrapsCodeAndData() {
		ApiResponse<String> response = ApiResponse.success("hello");

		assertThat(response.getCode()).isEqualTo("SUCCESS");
		assertThat(response.getData()).isEqualTo("hello");
		assertThat(response.getMessage()).isNull();
	}

	@Test
	void success_withoutData_hasNullData() {
		ApiResponse<Void> response = ApiResponse.success();

		assertThat(response.getCode()).isEqualTo("SUCCESS");
		assertThat(response.getData()).isNull();
	}

	@Test
	void error_wrapsCodeAndMessage_withNullData() {
		ApiResponse<Void> response = ApiResponse.error("MEMBER_NOT_FOUND", "존재하지 않는 회원입니다");

		assertThat(response.getCode()).isEqualTo("MEMBER_NOT_FOUND");
		assertThat(response.getMessage()).isEqualTo("존재하지 않는 회원입니다");
		assertThat(response.getData()).isNull();
	}
}
