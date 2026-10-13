package com.jjinmak.back.boundedContext.delivery.out.dto;

import java.util.List;

public record WebhookRegisterResponseDto(
    Data data,
    List<GraphQlError> errors
) {
    public record Data(Boolean registerTrackWebhook){}

    public record GraphQlError(
            String message,
            List<Location> locations,
            List<String> path,
            Extensions extensions
    ){}

    public record Location(Integer line, Integer column){}

    public record Extensions(DeliveryTrackerExtension code){}
}