package com.jjinmak.member.in.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

// 일반 회원가입(MemberSignUpRequestDto)에서 비밀번호를 빼고 가입 대기 토큰을 더한 형태
public record KakaoSignUpRequestDto(

        @NotBlank
        @Schema(description = "카카오 로그인 응답(SIGNUP_REQUIRED)으로 받은 가입 대기 토큰. 10분간 유효")
        String signupToken,

        // 카카오 로그인 응답에 email 이 있었으면(인증된 카카오 이메일) 서버는 이 값을 무시하고 그 이메일로 가입시킨다.
        // email 이 null 이었을 때만 필수다(서비스 계층에서 검사).
        @Email
        @Size(max = 255)
        @Schema(description = "이메일. 카카오 로그인 응답의 email 이 null 이었을 때만 필수이며, 값이 있었다면 무시된다", example = "user@example.com")
        String email,

        @NotBlank
        @Size(max = 50)
        @Schema(description = "닉네임", example = "TestUser123")
        String nickname,

        @NotBlank
        @Size(max = 50)
        @Schema(description = "사용자 실명", example = "홍길동")
        String username,

        @NotBlank
        @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 전화번호 형식이 아닙니다.")
        @Schema(description = "전화번호 (하이픈 제외)", example = "01012345678")
        String phone,

        @Schema(description = "생년월일 (선택)", example = "1999-01-21")
        LocalDate birthDate,

        @Valid
        @NotNull
        @Schema(description = "기본 배송지")
        AddressRequestDto address
) {
}
