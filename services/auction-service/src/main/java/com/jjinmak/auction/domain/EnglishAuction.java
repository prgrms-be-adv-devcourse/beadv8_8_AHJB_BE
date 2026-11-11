package com.jjinmak.auction.domain;


import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@DiscriminatorValue("ENGLISH")
@NoArgsConstructor(access= AccessLevel.PROTECTED)
public class EnglishAuction extends Auction{
    private Long instantWinPrice;
    private Long highestBidPrice;
    private Long highestBidderId;
    private Integer bidCount;
    private LocalDateTime endingSoonNotifiedAt;
    private EnglishAuction(Long productId, Long sellerId, Long startPrice, Long instantWinPrice,
                           Long shippingFee, String category,
                           LocalDateTime startAt, LocalDateTime endAt) {
        super(productId, sellerId, startPrice, shippingFee, category, startAt, endAt);
        this.instantWinPrice = instantWinPrice;
        this.bidCount = 0;
    }

    // 경매 조건은 상품 컨텍스트에서 검증된 값을 받는다.
    public static EnglishAuction create(Long productId, Long sellerId, Long startPrice,
                                        Long instantWinPrice, Long shippingFee, String category,
                                        LocalDateTime endAt, LocalDateTime startAt) {
        return new EnglishAuction(productId, sellerId, startPrice, instantWinPrice,
                shippingFee, category, startAt, endAt);
    }
    //오버라이드
    @Override
    public Long currentPriceAt(LocalDateTime now) {
        return getCurrentPrice();
    }
    @Override
    public AuctionCloseResult close(LocalDateTime now) {
        if (hasBids()) {
            win(highestBidderId, highestBidPrice, now);
            return AuctionCloseResult.WON;
        }
        fail(FailureReason.NO_BID);
        return AuctionCloseResult.FAILED;
    }

    //입찰
    public Long getCurrentPrice() {
        if (bidCount == 0) {
            return getStartPrice();
        }
        return highestBidPrice;
    }

    public long getBidUnit() {
        return BidIncrement.unitOf(getCurrentPrice());
    }

    public Long getMinBidPrice() {
        if (!hasBids()) {
            return getStartPrice();
        }
        return getCurrentPrice() + getBidUnit();
    }

    public void updateHighestBid(Long bidderId, Long price) {
        this.highestBidPrice = price;
        this.highestBidderId = bidderId;
        this.bidCount++;
    }

    public void extendEndAt(LocalDateTime newEndAt) {
        changeEndAt(newEndAt);
    }

    public void winInstantly(Long bidderId, LocalDateTime now) {
        updateHighestBid(bidderId, instantWinPrice);
        win(bidderId, instantWinPrice, now);
    }

    public boolean hasBids() {
        return bidCount > 0;
    }

    public boolean isHighestBidder(Long memberId) {
        return Objects.equals(highestBidderId, memberId);
    }

}
