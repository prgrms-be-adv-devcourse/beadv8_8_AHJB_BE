package com.jjinmak.auction.in.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AuctionBidRequestDto (
        @NotNull
        @Positive
        Long bidPrice
){
}
