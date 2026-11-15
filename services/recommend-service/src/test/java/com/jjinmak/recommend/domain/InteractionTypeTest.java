package com.jjinmak.recommend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class InteractionTypeTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({"WIN, 1.0", "BID, 0.6", "WISH, 0.4"})
    @DisplayName("방금 생긴 상호작용은 유형 가중치를 그대로 쓴다")
    void weightAt_now_returnsTypeWeight(InteractionType type, double expected) {
        assertThat(type.weightAt(NOW, NOW)).isEqualTo(expected);
    }

    @Test
    @DisplayName("14일이 지나면 가중치가 절반이 되고, 28일이 지나면 4분의 1이 된다")
    void weightAt_halvesEveryFourteenDays() {
        assertThat(InteractionType.BID.weightAt(NOW.minusDays(14), NOW)).isCloseTo(0.3, within(1e-9));
        assertThat(InteractionType.BID.weightAt(NOW.minusDays(28), NOW)).isCloseTo(0.15, within(1e-9));
    }

    @Test
    @DisplayName("지난 시간을 일 단위로 끊지 않고 연속으로 줄인다")
    void weightAt_decaysContinuously() {
        assertThat(InteractionType.WIN.weightAt(NOW.minusDays(7), NOW)).isCloseTo(Math.sqrt(0.5), within(1e-9));
        assertThat(InteractionType.WIN.weightAt(NOW.minusHours(12), NOW)).isCloseTo(Math.pow(0.5, 0.5 / 14), within(1e-9));
    }

    @Test
    @DisplayName("현재보다 늦은 시각의 상호작용도 유형 가중치보다 커지지 않는다")
    void weightAt_futureTime_isNotBoosted() {
        assertThat(InteractionType.WISH.weightAt(NOW.plusDays(1), NOW)).isEqualTo(0.4);
    }
}
