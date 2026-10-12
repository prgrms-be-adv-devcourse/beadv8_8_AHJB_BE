package com.jjinmak.back.boundedContext.delivery.out.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjinmak.back.boundedContext.delivery.domain.DeliveryStatusCode;

import java.time.OffsetDateTime;
import java.util.List;

public record DeliveryStatusResponseDto(
    Data data,
    List<GraphQlError> errors
) {
    public record Data(Track track){}

    public record GraphQlError(
            String message,
            Extensions extensions
    ) {}

    public record Extensions(
            String code
    ) {}

    public record Track(LastEvent lastEvent){}

    public record LastEvent(
            @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            OffsetDateTime time,
            Status status
    ){}

    public record Status(DeliveryStatusCode code){}
}