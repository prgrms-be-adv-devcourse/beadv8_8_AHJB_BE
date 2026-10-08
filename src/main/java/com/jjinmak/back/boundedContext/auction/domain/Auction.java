package com.jjinmak.back.boundedContext.auction.domain;

import com.jjinmak.back.global.exception.BadRequestException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_auction_product_round", columnNames = {"product_id", "round"})) //유니크조건
public class Auction extends BaseIdAndTime {
    private static final long START_DELAY_HOURS = 1;
    private static final Set<Integer> ALLOWED_DURATION_DAYS = Set.of(1, 3, 7);

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private int round;

    @Column(nullable = false)
    private Long startPrice;

    private Long instantWinPrice;

    @Column(nullable = false)
    private int duration;

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

    private Auction(Long productId, Long sellerId, int round, Long startPrice, Long instantWinPrice,
                     int duration, LocalDateTime startAt, LocalDateTime endAt) {
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
    public static Auction create(Long productId, Long sellerId, int round, Long startPrice,
                                 Long instantWinPrice,int duration, LocalDateTime now) {
        if (productId == null || sellerId == null || now == null) {
            throw new BadRequestException("AUCTION003","경매 생성에 필요한 값이 누락되었습니다.");
        }
        if (!ALLOWED_DURATION_DAYS.contains(duration)) {
            throw new BadRequestException("AUCTION004","경매 기간은 1일, 3일, 7일 중에서 선택해야 합니다.");
        }
        if (round < 1) {
            throw new BadRequestException("AUCTION005","경매 회차는 1 이상이어야 합니다.");
        }
        if(startPrice == null || startPrice <= 0){
            throw new BadRequestException("AUCTION006","시작가는 0원보다 커야합니다.");
        }
        if(instantWinPrice != null && instantWinPrice <= startPrice){
            throw new BadRequestException("AUCTION007","즉시낙찰가는 시작가보다 커야 합니다.");
        }

        LocalDateTime startAt = now.plusHours(START_DELAY_HOURS);
        LocalDateTime endAt = startAt.plusDays(duration);
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
