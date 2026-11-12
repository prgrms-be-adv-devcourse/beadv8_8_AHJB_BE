package com.jjinmak.recommend.out;

import java.time.LocalDateTime;

public record AuctionRow(
        Long auctionId,
        Long productId,
        String category,
        Long currentPrice,
        LocalDateTime endAt
) {
}
