package com.jjinmak.boundedContext.delivery.in.dto;

public record WebhookCallbackDto(
        String carrierId,
        String trackingNumber
) {
}
