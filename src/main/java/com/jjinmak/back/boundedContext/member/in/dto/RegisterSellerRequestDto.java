package com.jjinmak.back.boundedContext.member.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterSellerRequestDto(

        @NotBlank
        @Size(max = 50)
        String bank,

        @NotBlank
        @Pattern(regexp = "^[0-9]{10,16}$", message = "올바른 계좌번호 형식이 아닙니다.")
        String accountNumber,

        @NotBlank
        @Size(max = 50)
        String accountHolder
){}
