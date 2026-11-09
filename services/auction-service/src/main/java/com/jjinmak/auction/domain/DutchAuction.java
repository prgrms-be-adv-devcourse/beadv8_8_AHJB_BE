package com.jjinmak.auction.domain;

import com.jjinmak.common.exception.BusinessException;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.jjinmak.auction.exception.AuctionErrorCode.*;

// 가격은 저장하지 않고 DutchPricePolicy로 시각마다계산.
@Entity
@Getter
@DiscriminatorValue("DUTCH")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DutchAuction extends Auction {

    private Long floorPrice;
    private Integer dropRatePercent;
    private Integer dropIntervalSec;

    private DutchAuction(Long productId, Long sellerId, Long startPrice, Long floorPrice,
                         Integer dropRatePercent, Integer dropIntervalSec,
                         Long shippingFee, ProductSnapshot productSnapshot,
                         LocalDateTime startAt, LocalDateTime endAt) {
        super(productId, sellerId, startPrice, shippingFee, productSnapshot, startAt, endAt);
        this.floorPrice = floorPrice;
        this.dropRatePercent = dropRatePercent;
        this.dropIntervalSec = dropIntervalSec;
    }

    public static DutchAuction create(Long productId, Long sellerId, Long startPrice, Long floorPrice,
                                      Integer dropRatePercent, Integer dropIntervalSec,
                                      Long shippingFee, ProductSnapshot productSnapshot,
                                      LocalDateTime startAt) {
        DutchPricePolicy policy = DutchPricePolicy.of(
                startPrice, floorPrice, dropRatePercent, dropIntervalSec, startAt);
        return new DutchAuction(productId, sellerId, startPrice, floorPrice,
                dropRatePercent, dropIntervalSec, shippingFee, productSnapshot,
                startAt, policy.endAt());
    }

    @Override
    public Long currentPriceAt(LocalDateTime now) {
        return policy().priceAt(now);
    }

    @Override
    public AuctionCloseResult close(LocalDateTime now) {
        fail(FailureReason.NO_BID);
        return AuctionCloseResult.FAILED;
    }

    public void buy(Long memberId, Long expectedPrice, LocalDateTime now) {
        if (isEnded(now)) {
            throw new BusinessException(AUCTION_ALREADY_ENDED);
        }
        long price = currentPriceAt(now);
        if (expectedPrice == null || price > expectedPrice) {
            throw new BusinessException(DUTCH_PRICE_CHANGED);
        }
        win(memberId, price, now);
    }

    //조회용
    public Long nextPriceAt(LocalDateTime now) {
        return policy().nextPriceAt(now);
    }

    public LocalDateTime nextDropAt(LocalDateTime now) {
        return policy().nextDropAt(now);
    }

    public boolean isLastPriceAt(LocalDateTime now) {
        return policy().isLastPriceAt(now);
    }

    public long getDropAmount() {
        return policy().dropAmount();
    }

    private DutchPricePolicy policy() {
        return DutchPricePolicy.of(getStartPrice(), floorPrice, dropRatePercent, dropIntervalSec, getStartAt());
    }
}