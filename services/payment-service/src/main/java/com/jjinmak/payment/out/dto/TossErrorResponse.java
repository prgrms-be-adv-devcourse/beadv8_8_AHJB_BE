package com.jjinmak.payment.out.dto;


public record TossErrorResponse(
        String code,
        String message
) {
}
