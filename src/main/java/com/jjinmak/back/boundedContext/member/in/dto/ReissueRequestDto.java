package com.jjinmak.back.boundedContext.member.in.dto;

import jakarta.validation.constraints.NotBlank;

public record ReissueRequestDto(

        @NotBlank
        String refreshToken

) {}
