package com.jjinmak.back.boundedContext.review.domain;

import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseIdAndTime {

    private static final BigDecimal MIN_RATING = new BigDecimal("0.5");
    private static final BigDecimal MAX_RATING = new BigDecimal("5.0");
    private static final BigDecimal RATING_STEP = new BigDecimal("0.5");

    // 주문당 후기 하나
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, unique = true)
    private ReviewableOrder reviewableOrder;

    // 작성자 (구매자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private ReviewMember writer;

    // 후기를 받은 사람 (판매자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private ReviewMember target;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "review_content", nullable = false, columnDefinition = "TEXT")
    private String content;

    private LocalDateTime deletedAt;

    private Review(ReviewableOrder reviewableOrder, BigDecimal rating, String content) {
        this.reviewableOrder = reviewableOrder;
        this.writer = reviewableOrder.getBuyer();
        this.target = reviewableOrder.getSeller();
        this.rating = rating;
        this.content = content;
    }

    // 작성 가능 여부를 검사하고, 해당 주문을 작성 완료로 표시한다.
    public static Review create(ReviewableOrder reviewableOrder, Long writerId,
                                BigDecimal rating, String content, LocalDateTime now) {
        reviewableOrder.validateWritable(writerId, now);
        Review review = new Review(reviewableOrder, validateRating(rating), content);
        reviewableOrder.markReviewed();
        return review;
    }

    // 값이 있는 항목만 수정한다. (null이면 기존 값 유지)
    public void update(Long writerId, BigDecimal rating, String content) {
        validateWriter(writerId);
        if (rating != null) {
            this.rating = validateRating(rating);
        }
        if (content != null) {
            this.content = content;
        }
    }

    // 소프트 삭제. 삭제 후에도 같은 주문으로 다시 작성할 수 없다.
    public void delete(Long writerId, LocalDateTime now) {
        validateWriter(writerId);
        this.deletedAt = now;
    }

    private void validateWriter(Long writerId) {
        if (!writer.getId().equals(writerId)) {
            throw new BusinessException(ReviewErrorCode.NOT_REVIEW_WRITER);
        }
    }

    // 0.5 ~ 5.0, 0.5 단위만 허용
    private static BigDecimal validateRating(BigDecimal rating) {
        if (rating == null
                || rating.compareTo(MIN_RATING) < 0
                || rating.compareTo(MAX_RATING) > 0
                || rating.remainder(RATING_STEP).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ReviewErrorCode.INVALID_RATING);
        }
        return rating.setScale(1);
    }
}
