package com.jjinmak.ai.recommend.app.dto;

import com.jjinmak.ai.recommend.out.row.AuctionRow;
import com.jjinmak.ai.recommend.out.row.SearchAuctionRow;

import java.time.LocalDateTime;

public record AuctionResponseDto(
        Long auctionId,
        Long productId,
        String category,
        Long currentPrice,
        LocalDateTime endAt
){
    public static AuctionResponseDto from(AuctionRow row){
        return new AuctionResponseDto(
                row.auctionId(), row.productId(), row.category(), row.currentPrice(), row.endAt()
        );
    }

    public static AuctionResponseDto from(SearchAuctionRow row){
        return new AuctionResponseDto(
                row.auctionId(), row.productId(), row.category(), row.currentPrice(), row.endAt()
        );
    }
}
