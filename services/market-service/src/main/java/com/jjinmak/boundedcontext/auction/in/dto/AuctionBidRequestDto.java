package com.jjinmak.boundedcontext.auction.in.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AuctionBidRequestDto (
        @NotNull
        @Positive
        Long bidPrice
){
}
