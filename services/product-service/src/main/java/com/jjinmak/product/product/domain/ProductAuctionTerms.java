package com.jjinmak.product.product.domain;

import com.jjinmak.common.exception.BusinessException;
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
import java.util.Set;

// 수정 기간 동안 보관했다가 경매 요청 시 경매 컨텍스트로 전달하는 경매 조건
// - 공통: 경매 방식, 시작가
// - 영국식(ENGLISH): 즉시낙찰가, 기간, 직접 지정 종료 시각
// - 네덜란드식(DUTCH): 최저가, 하락률, 하락 주기
// 방식에 맞지 않는 값은 null이다. (DB CHECK 제약으로도 막는다)
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductAuctionTerms {

    // 직접 지정 종료 시각의 허용 범위 (경매 시작 예정 시각 기준)
    public static final Duration MIN_CUSTOM_PERIOD = Duration.ofDays(1);
    public static final Duration MAX_CUSTOM_PERIOD = Duration.ofDays(7);

    // 네덜란드식 선택지 (경매 서비스 DutchPricePolicy와 같은 규칙)
    public static final Set<Integer> DUTCH_DROP_RATES = Set.of(1, 2, 3);
    public static final Set<Integer> DUTCH_DROP_INTERVALS = Set.of(30, 60, 90, 120);
    private static final long DUTCH_ROUNDING_UNIT = 100L;

    // ===== 공통 =====
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private AuctionType auctionType;

    @Column(nullable = false)
    private Long startPrice;

    // ===== 영국식 =====
    private Long instantWinPrice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private AuctionDuration duration;

    // duration이 CUSTOM일 때만 값이 있다.
    private LocalDateTime customEndAt;

    // ===== 네덜란드식 =====
    private Long floorPrice;

    private Integer dropRatePercent;

    private Integer dropIntervalSec;

    private ProductAuctionTerms(AuctionType auctionType, Long startPrice,
                                Long instantWinPrice, AuctionDuration duration, LocalDateTime customEndAt,
                                Long floorPrice, Integer dropRatePercent, Integer dropIntervalSec) {
        this.auctionType = auctionType;
        this.startPrice = startPrice;
        this.instantWinPrice = instantWinPrice;
        this.duration = duration;
        this.customEndAt = customEndAt;
        this.floorPrice = floorPrice;
        this.dropRatePercent = dropRatePercent;
        this.dropIntervalSec = dropIntervalSec;
    }

    private static ProductAuctionTerms english(Long startPrice, Long instantWinPrice,
                                               AuctionDuration duration, LocalDateTime customEndAt) {
        return new ProductAuctionTerms(AuctionType.ENGLISH, startPrice,
                instantWinPrice, duration, customEndAt, null, null, null);
    }

    // 영국식 경매 조건 검증 후 생성한다. (판매자 등록, 수정에서 함께 사용)
    // auctionScheduledAt: 경매 시작 예정 시각. 직접 지정 종료 시각의 허용 범위 기준이 된다.
    public static ProductAuctionTerms create(Long startPrice, Long instantWinPrice,
                                             AuctionDuration duration, LocalDateTime customEndAt,
                                             LocalDateTime auctionScheduledAt) {
        if (instantWinPrice != null && instantWinPrice <= startPrice) {
            throw new BusinessException(ProductErrorCode.INVALID_INSTANT_WIN_PRICE);
        }

        if (duration != AuctionDuration.CUSTOM) {
            return english(startPrice, instantWinPrice, duration, null);
        }

        if (customEndAt == null) {
            throw new BusinessException(ProductErrorCode.DIRECT_INPUT_REQUIRED);
        }

        // 경매 시작 예정 시각 기준 최소 1일, 최대 7일 (고정 기간 1/3/7일과 같은 범위)
        if (customEndAt.isBefore(auctionScheduledAt.plus(MIN_CUSTOM_PERIOD))
                || customEndAt.isAfter(auctionScheduledAt.plus(MAX_CUSTOM_PERIOD))) {
            throw new BusinessException(ProductErrorCode.INVALID_CUSTOM_END_AT);
        }

        return english(startPrice, instantWinPrice, duration, customEndAt);
    }

    // 네덜란드식 경매 조건 검증 후 생성한다. (관리자 이벤트 등록에서 사용)
    // 경매 서비스(DutchPricePolicy)에서 거절되지 않도록 같은 규칙으로 미리 검증한다.
    public static ProductAuctionTerms createDutch(Long startPrice, Long floorPrice,
                                                  Integer dropRatePercent, Integer dropIntervalSec) {
        // 요청 DTO에서 @NotNull로 막지만, Set.of().contains(null)은 NPE라 여기서도 확인한다.
        if (floorPrice == null || floorPrice < 0 || floorPrice >= startPrice) {
            throw new BusinessException(ProductErrorCode.INVALID_DUTCH_FLOOR_PRICE);
        }
        if (dropRatePercent == null || !DUTCH_DROP_RATES.contains(dropRatePercent)) {
            throw new BusinessException(ProductErrorCode.INVALID_DUTCH_DROP_RATE);
        }
        if (dropIntervalSec == null || !DUTCH_DROP_INTERVALS.contains(dropIntervalSec)) {
            throw new BusinessException(ProductErrorCode.INVALID_DUTCH_DROP_INTERVAL);
        }
        if (dutchDropAmountOf(startPrice, dropRatePercent) < DUTCH_ROUNDING_UNIT) {
            throw new BusinessException(ProductErrorCode.DUTCH_DROP_AMOUNT_TOO_SMALL);
        }

        return new ProductAuctionTerms(AuctionType.DUTCH, startPrice,
                null, null, null, floorPrice, dropRatePercent, dropIntervalSec);
    }

    // 한 번에 내려가는 금액 = 시작가 × 하락률, 100원 단위 내림
    private static long dutchDropAmountOf(long startPrice, int dropRatePercent) {
        long raw = startPrice * dropRatePercent / 100;
        return raw / DUTCH_ROUNDING_UNIT * DUTCH_ROUNDING_UNIT;
    }

    public boolean isDutch() {
        return auctionType == AuctionType.DUTCH;
    }

    // 경매 종료 시각. 직접 지정이면 그 시각, 아니면 경매 시작 예정 시각 + 기간(1/3/7일)
    // 상품에서 확정해서 보내야 이벤트가 늦게 도착해도 안내한 종료 시각과 같아진다.
    // 네덜란드식은 누군가 사거나 최저가까지 내려가야 끝나서 미리 정해진 종료 시각이 없다. (null)
    public LocalDateTime resolveEndAt(LocalDateTime auctionScheduledAt) {
        if (isDutch()) {
            return null;
        }
        if (duration == AuctionDuration.CUSTOM) {
            return customEndAt;
        }
        return auctionScheduledAt.plusDays(duration.getDays());
    }
}
