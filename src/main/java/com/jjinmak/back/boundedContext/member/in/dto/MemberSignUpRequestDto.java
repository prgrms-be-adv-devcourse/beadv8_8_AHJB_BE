package com.jjinmak.back.boundedContext.member.in.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record MemberSignUpRequestDto(

        @Email
        @NotBlank
        @Schema(description = "로그인 아이디로 사용될 이메일", example = "user@example.com")
        String email,

        @NotBlank
        @Schema(description = "비밀번호", example = "password123!")
        String password,

        @NotBlank
        @Schema(description = "비밀번호 확인", example = "password123!")
        String passwordConfirm,

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
        @AssertTrue(message = "비밀번호가 일치하지 않습니다.")
        public boolean isPasswordMatched() {
                return password != null && password.equals(passwordConfirm);
        }
}