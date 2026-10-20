package com.jjinmak.back.boundedContext.auction.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(indexes = @Index(name = "idx_bid_bidder_auction", columnList = "bidder_id, auction_id"))
public class Bid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false) //입찰기록 변경 불가
    private Long auctionId;
    @Column(nullable = false, updatable = false)
    private Long bidderId;
    @Column(nullable = false, updatable = false)
    private Long bidPrice;
    @Column(nullable = false, updatable = false)
    private LocalDateTime bidAt;

    public Bid(Long auctionId,Long bidderId,Long bidPrice,LocalDateTime bidAt){
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidPrice = bidPrice;
        this.bidAt = bidAt;
    }

}
