package com.jjinmak.delivery.in.dto;

public record WebhookCallbackDto(
        String carrierId,
        String trackingNumber
) {
}
