package com.jjinmak.back.boundedContext.auction.domain;

import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction extends BaseIdAndTime {

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private Long startPrice;

    private Long instantWinPrice;

    private Long shippingFee;


    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private AuctionStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private FailureReason failureReason;

    private Long highestBidPrice;

    private Long highestBidderId;

    @Column(nullable = false)
    private int bidCount;

    private Long winnerId;

    private Long winningPrice;

    private LocalDateTime winAt;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Version
    private Long version;

    private Auction(Long productId, Long sellerId, Long startPrice, Long instantWinPrice,
                      Long shippingFee, LocalDateTime startAt, LocalDateTime endAt) {
        this.productId = productId;
        this.sellerId = sellerId;
        this.startPrice = startPrice;
        this.instantWinPrice = instantWinPrice;
        this.shippingFee = shippingFee;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = AuctionStatus.IN_PROGRESS;
        this.bidCount = 0;
    }

    // 경매 조건은 상품 컨텍스트에서 검증된 값을 받는다.
    public static Auction create(Long productId, Long sellerId, Long startPrice,
                                 Long instantWinPrice, Long shippingFee,
                                 LocalDateTime endAt, LocalDateTime now) {
        return new Auction(productId, sellerId, startPrice, instantWinPrice,
                shippingFee, now, endAt);
    }
}
