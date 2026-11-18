package com.jjinmak.member.in.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SocialLoginResponseDto(
        @Schema(description = "LOGIN: 로그인 완료(리프레시 토큰은 쿠키), SIGNUP_REQUIRED: 추가 정보 입력 필요")
        Status status,

        @Schema(description = "액세스 토큰 (LOGIN 일 때만)")
        String accessToken,

        @Schema(description = "정지 회원 여부 (LOGIN 일 때만)")
        Boolean suspended,

        @Schema(description = "가입 대기 토큰 (SIGNUP_REQUIRED 일 때만). 추가 정보와 함께 가입 API 로 보낸다")
        String signupToken
) {
    public enum Status {
        LOGIN, SIGNUP_REQUIRED
    }

    public static SocialLoginResponseDto loggedIn(String accessToken, boolean suspended) {
        return new SocialLoginResponseDto(Status.LOGIN, accessToken, suspended, null);
    }

    public static SocialLoginResponseDto signupRequired(String signupToken) {
        return new SocialLoginResponseDto(Status.SIGNUP_REQUIRED, null, null, signupToken);
    }
}
