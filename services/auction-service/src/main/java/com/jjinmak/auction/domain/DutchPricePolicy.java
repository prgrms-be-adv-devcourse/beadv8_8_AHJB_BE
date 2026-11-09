package com.jjinmak.auction.domain;

import java.time.Duration;
import java.time.LocalDateTime;


// 시작가 기준 선형 하락: 하락 주기마다 시작가 × 하락률만큼

public final class DutchPricePolicy {

    private static final long ROUNDING_UNIT = 100L;

    private final long startPrice;
    private final long dropAmount;
    private final Duration dropInterval;
    private final LocalDateTime startAt;
    private final long lastStep;

    private DutchPricePolicy(long startPrice, long dropAmount, Duration dropInterval,
                             LocalDateTime startAt, long lastStep) {
        this.startPrice = startPrice;
        this.dropAmount = dropAmount;
        this.dropInterval = dropInterval;
        this.startAt = startAt;
        this.lastStep = lastStep;
    }

    public static DutchPricePolicy of(long startPrice, long floorPrice, int dropRatePercent,
                                      int dropIntervalSec, LocalDateTime startAt) {
        if (startAt == null) {
            throw new IllegalArgumentException("하향식 시작 시각이 없습니다.");
        }
        if (floorPrice <= 0 || startPrice <= floorPrice) {
            throw new IllegalArgumentException(
                    "하향식 가격 조건이 올바르지 않습니다. startPrice=" + startPrice + ", floorPrice=" + floorPrice);
        }
        if (dropRatePercent <= 0 || dropIntervalSec <= 0) {
            throw new IllegalArgumentException(
                    "하향식 하락 조건이 올바르지 않습니다. dropRatePercent=" + dropRatePercent + ", dropIntervalSec=" + dropIntervalSec);
        }
        long dropAmount = dropAmountOf(startPrice, dropRatePercent);
        if (dropAmount < ROUNDING_UNIT) {
            throw new IllegalArgumentException("하향식 감소 금액이 100원보다 작습니다. dropAmount=" + dropAmount);
        }
        long lastStep = (startPrice - floorPrice) / dropAmount;   // 최저가 이상인 마지막 단계
        return new DutchPricePolicy(startPrice, dropAmount, Duration.ofSeconds(dropIntervalSec), startAt, lastStep);
    }

    // 감소 금액 = 시작가 × 하락률, 100원 단위 내림
    private static long dropAmountOf(long startPrice, int dropRatePercent) {
        long raw = startPrice * dropRatePercent / 100;
        return raw / ROUNDING_UNIT * ROUNDING_UNIT;
    }

    public long dropAmount() {
        return dropAmount;
    }

    public long lastStep() {
        return lastStep;
    }

    // 마지막 가격을 한 주기 유지한 다음 하락 시각. 경매가 유찰되는 시각이다.
    public LocalDateTime endAt() {
        return dropAt(lastStep + 1);
    }

    public long priceAt(LocalDateTime now) {
        return priceOf(stepAt(now));
    }

    public boolean isLastPriceAt(LocalDateTime now) {
        return stepAt(now) == lastStep;
    }

    // 다음 가격. 마지막 단계면 다음 가격이 없어서 null
    public Long nextPriceAt(LocalDateTime now) {
        long step = stepAt(now);
        return step == lastStep ? null : priceOf(step + 1);
    }

    // 다음 하락 시각. 마지막 단계면 null (그 시각은 하락이 아니라 유찰이고, 공개하지 않는다)
    public LocalDateTime nextDropAt(LocalDateTime now) {
        long step = stepAt(now);
        return step == lastStep ? null : dropAt(step + 1);
    }

    // 지금까지 지난 하락 횟수. 하락 시각 정각에는 새 가격이고,
    // 시작 전(서버 간 시계 오차)은 0, 마지막 단계를 넘지 않는다.
    private long stepAt(LocalDateTime now) {
        if (now.isBefore(startAt)) {
            return 0;
        }
        long passed = Duration.between(startAt, now).dividedBy(dropInterval);
        return Math.min(passed, lastStep);
    }

    private long priceOf(long step) {
        return startPrice - step * dropAmount;
    }

    private LocalDateTime dropAt(long step) {
        return startAt.plus(dropInterval.multipliedBy(step));
    }
}
