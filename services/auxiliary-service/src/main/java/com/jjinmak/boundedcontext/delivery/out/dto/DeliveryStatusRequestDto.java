package com.jjinmak.boundedcontext.delivery.out.dto;

public record DeliveryStatusRequestDto(
        String query,
        Variables variables
) {
    public record Variables(
            String carrierId,
            String trackingNumber
    ) {}
}
