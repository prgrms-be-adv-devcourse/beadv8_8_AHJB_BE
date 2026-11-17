package com.jjinmak.auction.domain;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.jjinmak.auction.domain.AuctionStatus.*;
import static com.jjinmak.auction.exception.AuctionErrorCode.*;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "auction_type", discriminatorType = DiscriminatorType.STRING, length = 20)
@Table(indexes = {
        @Index(name = "idx_auction_status_end_at", columnList = "status, end_at"),
        @Index(name = "idx_auction_status_category_end_at", columnList = "status, category, end_at")
})
public abstract class Auction extends BaseIdAndTime {

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private Long startPrice;

    private Long shippingFee;

    private String category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private AuctionStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private FailureReason failureReason;

    private Long winnerId;

    private Long winningPrice;

    private LocalDateTime winAt;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Version
    private Long version;

    protected Auction(Long productId, Long sellerId, Long startPrice,
                    Long shippingFee, String category,
                    LocalDateTime startAt, LocalDateTime endAt) {
        this.productId = productId;
        this.sellerId = sellerId;
        this.startPrice = startPrice;
        this.shippingFee = shippingFee;
        this.category = category;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = AuctionStatus.IN_PROGRESS;
    }

    // 지금 시점의 가격. 영국식은 입찰 기준이라 시각과 무관하고, 하향식은 시각으로 계산한다.
    public abstract Long currentPriceAt(LocalDateTime now);

    // 종료 시각이 지난 경매를 마감한다. 낙찰·유찰 규칙은 유형마다 다르다.
    public abstract AuctionCloseResult close(LocalDateTime now);
    public boolean isEnded(LocalDateTime now){
        return !now.isBefore(endAt);
    }

    // 하위 클래스가 종료 시각을 바꿀 때 쓴다. -->> 영국식 연장
    protected void changeEndAt(LocalDateTime newEndAt) {
        this.endAt = newEndAt;
    }

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
    public boolean isStatus(AuctionStatus status){return this.status==status;}
}
