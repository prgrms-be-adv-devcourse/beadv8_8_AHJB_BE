package com.jjinmak.contracts.action.event;

import java.time.LocalDateTime;

public record AuctionWonEvent (
        Long auctionId,
        Long productId,
        Long winnerId,
        Long sellerId,
        Long winningPrice,
        Long shippingFee,
        LocalDateTime winAt
){
}
