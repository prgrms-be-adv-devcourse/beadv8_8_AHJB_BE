package com.jjinmak.recommend.domain;

import com.jjinmak.common.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

import static com.jjinmak.recommend.domain.AuctionStatus.ACTIVE;
import static com.jjinmak.recommend.domain.AuctionStatus.ENDED;

@Entity
@Getter
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_auction_status_end_at", columnList = "status, end_at")
})
public class RecommendAuction extends BaseManualIdAndTime {

    @Column(nullable = false)
    private Long productId;

    private Long instantWinPrice;

    private Long currentPrice;

    @Column(nullable = false)
    private Long bidCount;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private AuctionStatus status;

    public RecommendAuction(
            Long auctionId, Long productId,
            Long instantWinPrice, Long startPrice,
            LocalDateTime startAt, LocalDateTime endAt
    ){
        super(auctionId);
        this.productId = productId;
        this.instantWinPrice = instantWinPrice;
        this.currentPrice = startPrice;
        this.bidCount = 0L;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = ACTIVE;
    }

    public void won(Long winningPrice, LocalDateTime winAt){
        this.currentPrice = winningPrice;
        this.endedAt = winAt;
        this.status = ENDED;
    }

    public void fail(LocalDateTime failedAt){
        this.endedAt = failedAt;
        this.status = ENDED;
    }

    public void setEndAt(LocalDateTime endAt){
        if (endAt.isAfter(this.endAt))
            this.endAt = endAt;
    }
}
