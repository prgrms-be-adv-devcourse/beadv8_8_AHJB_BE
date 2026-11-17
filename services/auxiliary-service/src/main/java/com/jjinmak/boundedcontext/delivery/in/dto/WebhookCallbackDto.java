package com.jjinmak.boundedcontext.delivery.in.dto;

public record WebhookCallbackDto(
        String carrierId,
        String trackingNumber
) {
}
