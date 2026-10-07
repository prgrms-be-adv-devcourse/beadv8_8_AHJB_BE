package com.jjinmak.back.boundedContext.auction.in.dto;

import com.jjinmak.back.boundedContext.auction.domain.AuctionDuration;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AuctionCreateRequestDto(
        @NotNull
        Long productId,
        @NotNull
        @Positive
        Long startPrice,
        @Positive Long instantWinPrice,
        @NotNull
        AuctionDuration duration// "ONE_DAY", "THREE_DAYS", "SEVEN_DAYS"...

) { }
