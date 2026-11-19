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
        String signupToken,

        @Schema(description = "카카오가 인증한 이메일 (SIGNUP_REQUIRED 일 때만). 있으면 이 이메일로 가입되므로 화면에서 수정할 수 없게 한다. 없으면 null 이며 직접 입력받는다")
        String email,

        @Schema(description = "카카오 닉네임 (SIGNUP_REQUIRED 일 때만). 가입 화면을 미리 채우는 제안값으로 사용자가 수정할 수 있다. 중복·길이 검증은 가입 API 에서 한다. 없으면 null")
        String nickname
) {
    public enum Status {
        LOGIN, SIGNUP_REQUIRED
    }

    public static SocialLoginResponseDto loggedIn(String accessToken, boolean suspended) {
        return new SocialLoginResponseDto(Status.LOGIN, accessToken, suspended, null, null, null);
    }

    public static SocialLoginResponseDto signupRequired(String signupToken, String email, String nickname) {
        return new SocialLoginResponseDto(Status.SIGNUP_REQUIRED, null, null, signupToken, email, nickname);
    }
}
