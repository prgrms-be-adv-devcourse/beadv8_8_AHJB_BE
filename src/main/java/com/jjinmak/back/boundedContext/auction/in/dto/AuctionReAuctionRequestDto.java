package com.jjinmak.back.boundedContext.auction.in.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AuctionReAuctionRequestDto(
        @NotNull
        Long productId,
        @NotNull
        @Positive
        Long startPrice,
        @Positive Long instantWinPrice,
        @NotNull
        Integer duration

) { }
