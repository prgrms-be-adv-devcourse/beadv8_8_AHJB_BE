package com.jjinmak.back.boundedContext.auction.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
//인덱스추가예정
public class Bid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false) //입찰기록 변경 불가
    private Long auctionId;
    @Column(nullable = false, updatable = false)
    private UUID bidderId;
    @Column(nullable = false, updatable = false)
    private Long bidPrice;
    @Column(nullable = false, updatable = false)
    private LocalDateTime bidAt;

    public Bid(Long auctionId,UUID bidderId,Long bidPrice,LocalDateTime bidAt){
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidPrice = bidPrice;
        this.bidAt = bidAt;
    }

}
