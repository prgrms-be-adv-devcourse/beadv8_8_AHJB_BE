package com.jjinmak.boundedcontext.market.app;

import com.jjinmak.boundedcontext.auction.dto.AuctionCreateDto;
import com.jjinmak.boundedcontext.product.product.in.dto.ProductAuctionOpenDto;

// 상품의 판매 조건을 경매 생성 요청으로 바꾼다. (경매 시작, 재등록이 같이 쓴다)
final class MarketAuctionRequests {

    private MarketAuctionRequests() {
    }

    static AuctionCreateDto from(ProductAuctionOpenDto open) {
        return switch (open.auctionType()) {
            case ENGLISH -> AuctionCreateDto.english(open.productId(), open.round(), open.sellerId(), open.startPrice(),
                    open.shippingFee(), open.startAt(), open.category(),
                    open.instantWinPrice(), open.endAt());
            case DUTCH -> AuctionCreateDto.dutch(open.productId(), open.round(), open.sellerId(), open.startPrice(),
                    open.shippingFee(), open.startAt(), open.category(),
                    open.floorPrice(), open.dropRatePercent(), open.dropIntervalSec());
        };
    }
}
