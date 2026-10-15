package com.jjinmak.back.boundedContext.auction.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.jjinmak.back.boundedContext.auction.domain.AuctionStatus.*;
import static com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode.*;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_auction_status_end_at", columnList = "status, end_at"))
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

    // 입찰
    public Long getCurrentPrice(){
        if(bidCount==0){return startPrice;}
        return highestBidPrice;
    }
    public long getBidUnit(){
        return BidIncrement.unitOf(getCurrentPrice());
    }
    public Long getMinBidPrice(){
        if(!hasBids()){return startPrice;}
        return getCurrentPrice()+getBidUnit();
    }
    public boolean isEnded(LocalDateTime now){
        return !now.isBefore(endAt);
    }

    public void updateHighestBid(Long bidderId, Long price){
        this.highestBidPrice=price;
        this.highestBidderId=bidderId;
        this.bidCount++;
    }
    public void extendEndAt(LocalDateTime newEndAt){
        this.endAt=newEndAt;
    }
    //즉시낙찰
    public void winInstantly(Long bidderId, LocalDateTime now){
        updateHighestBid(bidderId,instantWinPrice);
        win(bidderId,instantWinPrice,now);
    }
    //낙찰관련 메소드
    public boolean hasBids(){return bidCount>0;}

    public void win(Long winnerId,Long price,LocalDateTime now){
        validateStatus(IN_PROGRESS);
        this.status= WON;
        this.winnerId=winnerId;
        this.winningPrice=price;
        this.winAt=now;
    }
    public void fail(FailureReason reason){
        validateStatus(reason.getFrom());
        this.status=FAILED;
        this.failureReason = reason;
    }
    public void pay(){
        validateStatus(WON);
        this.status = PAID;
    }
    public void complete(){
        validateStatus(PAID);
        this.status = COMPLETED;
    }
    private void validateStatus(AuctionStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(INVALID_STATUS_TRANSITION);
        }
    }
    //상태
    public boolean isInProgress(){
        return status == IN_PROGRESS;
    }
    public boolean isSeller(Long memberId){
        return Objects.equals(sellerId, memberId);
    }
    public boolean isHighestBidder(Long memberId){
        return Objects.equals(highestBidderId, memberId);
    }
    public boolean isStatus(AuctionStatus status){return this.status==status;}
}
