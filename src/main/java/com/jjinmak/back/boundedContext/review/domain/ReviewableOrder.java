package com.jjinmak.back.boundedContext.review.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

// 구매확정 이벤트로 생성되는 "후기 작성 가능 주문". 주문당 하나만 존재한다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewableOrder extends BaseIdAndTime {

    // 구매확정 후 이 기간 동안 후기를 작성할 수 있다.
    public static final Duration REVIEW_PERIOD = Duration.ofDays(30);

    @Column(nullable = false, unique = true)
    private Long orderId;

    // 후기 작성자 (구매자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private ReviewMember buyer;

    // 후기 대상 (판매자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private ReviewMember seller;

    @Column(nullable = false)
    private Long productId;

    // 구매확정 시점의 상품명 스냅샷
    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long winningPrice;

    @Column(nullable = false)
    private LocalDateTime confirmedAt;

    @Column(nullable = false)
    private LocalDateTime reviewDeadline;

    // 후기를 한 번이라도 작성했는지 (삭제해도 다시 작성할 수 없으므로 되돌리지 않는다)
    @Column(nullable = false)
    private boolean reviewed;

    public ReviewableOrder(Long orderId, ReviewMember buyer, ReviewMember seller,
                           Long productId, String productName, Long winningPrice, LocalDateTime confirmedAt) {
        this.orderId = orderId;
        this.buyer = buyer;
        this.seller = seller;
        this.productId = productId;
        this.productName = productName;
        this.winningPrice = winningPrice;
        this.confirmedAt = confirmedAt;
        this.reviewDeadline = confirmedAt.plus(REVIEW_PERIOD);
        this.reviewed = false;
    }

    public void validateWritable(Long writerId, LocalDateTime now) {
        if (!buyer.getId().equals(writerId)) {
            throw new BusinessException(ReviewErrorCode.NOT_ORDER_BUYER);
        }
        if (reviewed) {
            throw new BusinessException(ReviewErrorCode.ALREADY_REVIEWED);
        }
        if (!now.isBefore(reviewDeadline)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_PERIOD_EXPIRED);
        }
    }

    public void markReviewed() {
        this.reviewed = true;
    }
}
