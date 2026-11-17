package com.jjinmak.back.payment.out.dto;


public record TossErrorResponse(
        String code,
        String message
) {
}
