package com.jjinmak.back.boundedContext.product.domain;

import com.jjinmak.back.global.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDateTime;

// 수정 기간 동안 보관했다가 경매 요청 시 경매 컨텍스트로 전달하는 경매 조건
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductAuctionTerms {

    // 직접 지정 종료 시각의 허용 범위 (경매 시작 예정 시각 기준)
    public static final Duration MIN_CUSTOM_PERIOD = Duration.ofDays(1);
    public static final Duration MAX_CUSTOM_PERIOD = Duration.ofDays(7);

    @Column(nullable = false)
    private Long startPrice;

    private Long instantWinPrice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private AuctionDuration duration;

    // duration이 CUSTOM일 때만 값이 있다.
    private LocalDateTime customEndAt;

    private ProductAuctionTerms(Long startPrice, Long instantWinPrice, AuctionDuration duration, LocalDateTime customEndAt) {
        this.startPrice = startPrice;
        this.instantWinPrice = instantWinPrice;
        this.duration = duration;
        this.customEndAt = customEndAt;
    }

    // 경매 조건 검증 후 생성한다. (등록, 수정에서 함께 사용)
    // auctionScheduledAt: 경매 시작 예정 시각. 직접 지정 종료 시각의 허용 범위 기준이 된다.
    public static ProductAuctionTerms create(Long startPrice, Long instantWinPrice,
                                             AuctionDuration duration, LocalDateTime customEndAt,
                                             LocalDateTime auctionScheduledAt) {
        if (instantWinPrice != null && instantWinPrice <= startPrice) {
            throw new BusinessException(ProductErrorCode.INVALID_INSTANT_WIN_PRICE);
        }

        if (duration != AuctionDuration.CUSTOM) {
            return new ProductAuctionTerms(startPrice, instantWinPrice, duration, null);
        }

        if (customEndAt == null) {
            throw new BusinessException(ProductErrorCode.DIRECT_INPUT_REQUIRED);
        }

        // 경매 시작 예정 시각 기준 최소 1일, 최대 7일 (고정 기간 1/3/7일과 같은 범위)
        if (customEndAt.isBefore(auctionScheduledAt.plus(MIN_CUSTOM_PERIOD))
                || customEndAt.isAfter(auctionScheduledAt.plus(MAX_CUSTOM_PERIOD))) {
            throw new BusinessException(ProductErrorCode.INVALID_CUSTOM_END_AT);
        }

        return new ProductAuctionTerms(startPrice, instantWinPrice, duration, customEndAt);
    }
}
