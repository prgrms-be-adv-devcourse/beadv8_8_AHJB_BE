package com.jjinmak.delivery.in.dto;

import com.jjinmak.contracts.delivery.domain.CarrierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeliveryDataDto(
        @NotNull
        CarrierType carrier,

        @NotBlank
        String waybill
) {
}
