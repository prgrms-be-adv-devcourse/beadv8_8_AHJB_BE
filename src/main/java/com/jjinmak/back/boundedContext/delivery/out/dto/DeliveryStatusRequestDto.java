package com.jjinmak.back.boundedContext.delivery.out.dto;

public record DeliveryStatusRequestDto(
        String query,
        Variables variables
) {
    public record Variables(
            String carrierId,
            String trackingNumber
    ) {}
}