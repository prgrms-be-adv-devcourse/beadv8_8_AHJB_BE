package com.jjinmak.back.boundedContext.order.in.dto;

import com.jjinmak.back.shared.delivery.domain.CarrierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderDeliveryDto(

        @NotNull
        CarrierType carrier,

        @NotBlank
        String waybill
) {
}
