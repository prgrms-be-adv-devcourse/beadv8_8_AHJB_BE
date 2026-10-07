package com.jjinmak.back.boundedContext.auction.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@EnableJpaAuditing
public class Auction {
    private static final long START_DELAY_HOURS = 1;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private int round;

    @Column(nullable = false)
    private Long startPrice;

    private Long instantWinPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionDuration duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    @Enumerated(EnumType.STRING)
    private FailureReason failureReason;

    private Long highestBidPrice;

    private UUID highestBidderId;

    @Column(nullable = false)
    private int bidCount;

    private UUID winnerId;

    private Long winningPrice;

    private LocalDateTime winAt;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Auction(Long productId, UUID sellerId, int round, Long startPrice, Long instantWinPrice,
                    AuctionDuration duration, LocalDateTime startAt, LocalDateTime endAt) {
        this.productId = productId;
        this.sellerId = sellerId;
        this.round = round;
        this.startPrice = startPrice;
        this.instantWinPrice = instantWinPrice;
        this.duration = duration;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = AuctionStatus.READY;
        this.bidCount = 0;
    }
    public static Auction create(Long productId, UUID sellerId, int round, Long startPrice,
                                 Long instantWinPrice, AuctionDuration duration, LocalDateTime now) {
        if (productId == null || sellerId == null || duration == null || now == null) {
            throw new IllegalArgumentException("경매 생성에 필요한 값이 누락되었습니다.");
        }
        if (round < 1) {
            throw new IllegalArgumentException("경매 회차는 1 이상이어야 합니다.");
        }
        if(startPrice == null || startPrice <= 0){
            throw new IllegalArgumentException("시작가는 0원보다 커야합니다.");
        }
        if(instantWinPrice != null && instantWinPrice <= startPrice){
            throw new IllegalArgumentException("즉시낙찰가는 시작가보다 커야 합니다.");
        }

        LocalDateTime startAt = now.plusHours(START_DELAY_HOURS);
        LocalDateTime endAt = startAt.plusDays(duration.getDays());
        return new Auction(productId, sellerId, round, startPrice, instantWinPrice,
                duration, startAt, endAt);
    }
    public void start(LocalDateTime now){
        if(this.status != AuctionStatus.READY){
            throw new IllegalStateException("시작 대기 상태의 경매만 시작할 수 있습니다.");
        }
        if(now.isBefore(this.startAt)){
            throw new IllegalStateException("아직 시작 시간이 아닙니다.");
        }
        this.status = AuctionStatus.IN_PROGRESS;
    }
}
