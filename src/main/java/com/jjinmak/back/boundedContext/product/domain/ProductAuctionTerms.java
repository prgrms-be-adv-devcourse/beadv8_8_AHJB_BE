package com.jjinmak.back.boundedContext.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// 수정 기간 동안 보관했다가 경매 요청 시 경매 컨텍스트로 전달하는 경매 조건
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductAuctionTerms {

    @Column(nullable = false)
    private Long startPrice;

    private Long instantWinPrice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private AuctionDuration duration;

    // duration이 CUSTOM일 때만 값이 있다.
    private LocalDateTime customEndAt;

    public ProductAuctionTerms(Long startPrice, Long instantWinPrice, AuctionDuration duration, LocalDateTime customEndAt) {
        this.startPrice = startPrice;
        this.instantWinPrice = instantWinPrice;
        this.duration = duration;
        this.customEndAt = duration == AuctionDuration.CUSTOM ? customEndAt : null;
    }
}
