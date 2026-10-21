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

// 경매 이벤트로 복제한 경매 상태와 결과. '내 상품' 목록에 쓴다.
// 현재가·입찰 수·낙찰가는 복제하지 않는다. (입찰마다 이벤트를 보내지 않음, 기능명세서 2-01-3)
// 같은 이벤트가 두 번 와도 결과가 같도록 전이는 모두 멱등이다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductAuctionState {

    // 컬럼 추가 전 상품이 있어 nullable. null이면 Product가 경매 id로 상태를 추정한다.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "auction_status")
    private ProductAuctionStatus status;

    // 경매 시작 이벤트로 받은 종료 시각. 연장은 반영하지 않는다. (정확한 시각은 경매 상세)
    @Column(name = "auction_end_at")
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "auction_failure_reason")
    private ProductAuctionFailureReason failureReason;

    private ProductAuctionState(ProductAuctionStatus status) {
        this.status = status;
    }

    public static ProductAuctionState scheduled() {
        return new ProductAuctionState(ProductAuctionStatus.SCHEDULED);
    }

    // 상태 컬럼이 비어 있는 기존 상품을 추정한 상태로 채울 때 쓴다.
    public static ProductAuctionState restore(ProductAuctionStatus status) {
        return new ProductAuctionState(status);
    }

    // 시작 전일 때만 진행 중으로 바꾼다.
    public void start(LocalDateTime endAt) {
        if (status != ProductAuctionStatus.SCHEDULED) {
            return;
        }
        this.status = ProductAuctionStatus.IN_PROGRESS;
        this.endAt = endAt;
    }

    // 이미 끝난 경매(낙찰/무산)는 무시한다.
    public void markSold() {
        if (isEnded()) {
            return;
        }
        this.status = ProductAuctionStatus.SOLD;
    }

    // 낙찰 후 미결제·환불도 무산이므로 SOLD에서도 바뀐다. 이미 무산이면 무시한다.
    public void markFailed(ProductAuctionFailureReason reason) {
        if (status == ProductAuctionStatus.FAILED) {
            return;
        }
        this.status = ProductAuctionStatus.FAILED;
        this.failureReason = reason;
    }

    private boolean isEnded() {
        return status == ProductAuctionStatus.SOLD || status == ProductAuctionStatus.FAILED;
    }
}
