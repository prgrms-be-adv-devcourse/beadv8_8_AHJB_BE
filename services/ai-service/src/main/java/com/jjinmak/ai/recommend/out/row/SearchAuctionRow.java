package com.jjinmak.ai.recommend.out.row;

import java.time.LocalDateTime;

public record SearchAuctionRow(
        Long auctionId,
        Long productId,
        String productName,
        String category,
        String manufacturer,
        Long currentPrice,
        LocalDateTime endAt
) {
}
