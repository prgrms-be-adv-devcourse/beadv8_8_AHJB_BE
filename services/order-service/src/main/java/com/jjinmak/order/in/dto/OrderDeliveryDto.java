package com.jjinmak.order.in.dto;

import com.jjinmak.contracts.delivery.domain.CarrierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderDeliveryDto(

        @NotNull
        CarrierType carrier,

        @NotBlank
        String waybill
) {
}
