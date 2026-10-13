package com.jjinmak.back.boundedContext.delivery.out.dto;

import java.time.OffsetDateTime;

public record WebhookRegisterRequestDto(
    String query,
    Variables variables
) {

    public record Variables(Input input){}

    public record Input(
            String carrierId,
            String trackingNumber,
            String callbackUrl,
            OffsetDateTime expirationTime
    ){}
}
