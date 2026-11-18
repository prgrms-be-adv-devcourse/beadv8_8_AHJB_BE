package com.jjinmak.back.boundedContext.member.in.dto;

import java.time.LocalDate;

public record SellerInfoResponseDto(
        String bank,
        String accountNumberMasked,
        String accountHolder,
        LocalDate registeredAt
) {}
