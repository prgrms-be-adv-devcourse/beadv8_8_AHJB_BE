package com.jjinmak.back.boundedContext.product.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProductAuctionStateTest {

    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 10, 24, 12, 0);

    @Test
    @DisplayName("시작 전 상태에서 시작하면 진행 중이 되고 종료 시각을 기록한다")
    void start() {
        ProductAuctionState state = ProductAuctionState.scheduled();

        state.start(END_AT);

        assertThat(state.getStatus()).isEqualTo(ProductAuctionStatus.IN_PROGRESS);
        assertThat(state.getEndAt()).isEqualTo(END_AT);
    }

    @Test
    @DisplayName("이미 끝난 경매에 시작 이벤트가 다시 와도 상태가 바뀌지 않는다")
    void start_ignoredAfterEnded() {
        ProductAuctionState state = ProductAuctionState.scheduled();
        state.start(END_AT);
        state.markSold();

        state.start(END_AT.plusDays(1));

        assertThat(state.getStatus()).isEqualTo(ProductAuctionStatus.SOLD);
        assertThat(state.getEndAt()).isEqualTo(END_AT);
    }

    @Test
    @DisplayName("진행 중에 낙찰되면 SOLD가 된다")
    void markSold() {
        ProductAuctionState state = ProductAuctionState.scheduled();
        state.start(END_AT);

        state.markSold();

        assertThat(state.getStatus()).isEqualTo(ProductAuctionStatus.SOLD);
        assertThat(state.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("진행 중에 유찰되면 FAILED와 사유를 남긴다")
    void markFailed_noBid() {
        ProductAuctionState state = ProductAuctionState.scheduled();
        state.start(END_AT);

        state.markFailed(ProductAuctionFailureReason.NO_BID);

        assertThat(state.getStatus()).isEqualTo(ProductAuctionStatus.FAILED);
        assertThat(state.getFailureReason()).isEqualTo(ProductAuctionFailureReason.NO_BID);
    }

    @Test
    @DisplayName("낙찰 후 미결제로 무산되면 SOLD에서 FAILED로 바뀐다")
    void markFailed_afterSold() {
        ProductAuctionState state = ProductAuctionState.scheduled();
        state.start(END_AT);
        state.markSold();

        state.markFailed(ProductAuctionFailureReason.UNPAID);

        assertThat(state.getStatus()).isEqualTo(ProductAuctionStatus.FAILED);
        assertThat(state.getFailureReason()).isEqualTo(ProductAuctionFailureReason.UNPAID);
    }

    @Test
    @DisplayName("무산 후에는 낙찰·무산 이벤트가 다시 와도 처음 사유가 유지된다")
    void ignoredAfterFailed() {
        ProductAuctionState state = ProductAuctionState.scheduled();
        state.start(END_AT);
        state.markFailed(ProductAuctionFailureReason.NO_BID);

        state.markSold();
        state.markFailed(ProductAuctionFailureReason.REFUNDED);

        assertThat(state.getStatus()).isEqualTo(ProductAuctionStatus.FAILED);
        assertThat(state.getFailureReason()).isEqualTo(ProductAuctionFailureReason.NO_BID);
    }
}
