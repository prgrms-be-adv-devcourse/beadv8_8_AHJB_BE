package com.jjinmak.auction.in.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DutchBuyRequestDto(
        @NotNull
        @PositiveOrZero
        Long expectedPrice
) {
}