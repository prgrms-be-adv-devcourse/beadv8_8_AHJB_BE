package com.jjinmak.back.boundedContext.member.in.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequestDto(

        @Size(max = 50)
        @Schema(description = "배송지 별칭 (선택)", example = "집")
        String alias,

        @NotBlank
        @Size(max = 50)
        @Schema(description = "수령인", example = "홍길동")
        String recipient,

        @NotBlank
        @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 전화번호 형식이 아닙니다.")
        @Schema(description = "수령인 연락처", example = "01012345678")
        String recipientPhone,

        @NotBlank
        @Size(max = 10)
        @Schema(description = "우편번호", example = "06236")
        String zipcode,

        @NotBlank
        @Size(max = 255)
        @Schema(description = "기본 주소", example = "서울시 강남구 테헤란로 123")
        String address,

        @Size(max = 255)
        @Schema(description = "상세 주소 (선택)", example = "101동 1001호")
        String addressDetail
) {}