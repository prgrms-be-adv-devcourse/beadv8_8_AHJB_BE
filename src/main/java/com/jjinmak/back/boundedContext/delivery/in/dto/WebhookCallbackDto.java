package com.jjinmak.back.boundedContext.delivery.in.dto;

public record WebhookCallbackDto(
        String carrierId,
        String trackingNumber
) {
}
