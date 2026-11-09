package com.jjinmak.auction.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class DutchPricePolicyTest {

    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 13, 21, 0);

    // 시작가 30만, 최저가 15만, 2%, 60초 → 6,000원씩, 마지막 단계 25(150,000원), 21:26 유찰
    private DutchPricePolicy 기본_정책() {
        return DutchPricePolicy.of(300_000L, 150_000L, 2, 60, START_AT);
    }

    @Nested
    class 감소_금액 {

        @Test
        void 시작가에_하락률을_곱한다() {
            assertThat(기본_정책().dropAmount()).isEqualTo(6_000L);
        }

        @Test
        void 백원_단위로_내린다() {
            // 45,650 × 3% = 1,369.5 → 1,300
            DutchPricePolicy policy = DutchPricePolicy.of(45_650L, 30_000L, 3, 60, START_AT);

            assertThat(policy.dropAmount()).isEqualTo(1_300L);
        }

        @Test
        void 백원보다_작으면_만들_수_없다() {
            // 9,000 × 1% = 90
            assertThatThrownBy(() -> DutchPricePolicy.of(9_000L, 5_000L, 1, 60, START_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 마지막_단계와_종료_시각 {

        @Test
        void 최저가_이상인_마지막_단계를_구한다() {
            DutchPricePolicy policy = 기본_정책();

            assertThat(policy.lastStep()).isEqualTo(25);
            assertThat(policy.priceAt(START_AT.plusMinutes(25))).isEqualTo(150_000L);
        }

        @Test
        void 마지막_가격이_최저가로_나누어떨어지지_않아도_최저가_아래로_내려가지_않는다() {
            // 70만, 최저가 45만, 2% → 14,000원씩, 마지막 단계 17(462,000원)
            DutchPricePolicy policy = DutchPricePolicy.of(700_000L, 450_000L, 2, 60, START_AT);

            assertThat(policy.lastStep()).isEqualTo(17);
            assertThat(policy.priceAt(START_AT.plusMinutes(17))).isEqualTo(462_000L);
            assertThat(policy.priceAt(START_AT.plusMinutes(30))).isEqualTo(462_000L);
        }

        @Test
        void 마지막_가격을_한_주기_유지한_뒤에_끝난다() {
            assertThat(기본_정책().endAt()).isEqualTo(START_AT.plusMinutes(26));
        }

        @Test
        void 최저가가_0이면_0원까지_내려간다() {
            // 30만, 최저가 0, 2% → 6,000원씩, 마지막 단계 50(0원), 51분 뒤 유찰
            DutchPricePolicy policy = DutchPricePolicy.of(300_000L, 0L, 2, 60, START_AT);

            assertThat(policy.lastStep()).isEqualTo(50);
            assertThat(policy.priceAt(START_AT.plusMinutes(50))).isEqualTo(0L);
            assertThat(policy.isLastPriceAt(START_AT.plusMinutes(50))).isTrue();
            assertThat(policy.nextPriceAt(START_AT.plusMinutes(50))).isNull();
            assertThat(policy.endAt()).isEqualTo(START_AT.plusMinutes(51));
        }

        @ParameterizedTest(name = "주기 {0}초 → 종료 {1}초 뒤")
        @CsvSource({"30, 780", "60, 1560", "90, 2340", "120, 3120"})
        void 하락_주기만큼_종료_시각이_늘어난다(int intervalSec, long endAfterSec) {
            DutchPricePolicy policy = DutchPricePolicy.of(300_000L, 150_000L, 2, intervalSec, START_AT);

            assertThat(policy.endAt()).isEqualTo(START_AT.plusSeconds(endAfterSec));
        }
    }

    @Nested
    class 현재가 {

        @Test
        void 시작_시각에는_시작가다() {
            assertThat(기본_정책().priceAt(START_AT)).isEqualTo(300_000L);
        }

        @Test
        void 하락_시각_직전까지는_이전_가격이다() {
            assertThat(기본_정책().priceAt(START_AT.plusSeconds(59).plusNanos(999_000_000)))
                    .isEqualTo(300_000L);
        }

        @Test
        void 하락_시각_정각에는_새_가격이다() {
            assertThat(기본_정책().priceAt(START_AT.plusSeconds(60))).isEqualTo(294_000L);
        }

        @Test
        void 지난_하락_횟수만큼_내려간다() {
            assertThat(기본_정책().priceAt(START_AT.plusMinutes(10).plusSeconds(30))).isEqualTo(240_000L);
        }

        @Test
        void 시작_전이면_시작가다() {
            // 서버 간 시계 오차로 시작 시각보다 앞선 요청
            assertThat(기본_정책().priceAt(START_AT.minusSeconds(3))).isEqualTo(300_000L);
        }

        @Test
        void 종료_시각이_지나도_최저가_아래로_내려가지_않는다() {
            assertThat(기본_정책().priceAt(START_AT.plusHours(3))).isEqualTo(150_000L);
        }
    }

    @Nested
    class 다음_가격과_다음_하락_시각 {

        @Test
        void 다음_단계의_가격과_시각을_알려준다() {
            DutchPricePolicy policy = 기본_정책();
            LocalDateTime now = START_AT.plusMinutes(10).plusSeconds(30);

            assertThat(policy.nextPriceAt(now)).isEqualTo(234_000L);
            assertThat(policy.nextDropAt(now)).isEqualTo(START_AT.plusMinutes(11));
            assertThat(policy.isLastPriceAt(now)).isFalse();
        }

        @Test
        void 시작_전이면_첫_하락을_알려준다() {
            DutchPricePolicy policy = 기본_정책();
            LocalDateTime now = START_AT.minusSeconds(3);

            assertThat(policy.nextPriceAt(now)).isEqualTo(294_000L);
            assertThat(policy.nextDropAt(now)).isEqualTo(START_AT.plusMinutes(1));
        }

        @Test
        void 마지막_단계에서는_다음_가격과_시각이_없다() {
            DutchPricePolicy policy = 기본_정책();
            LocalDateTime now = START_AT.plusMinutes(25).plusSeconds(10);

            assertThat(policy.isLastPriceAt(now)).isTrue();
            assertThat(policy.nextPriceAt(now)).isNull();
            assertThat(policy.nextDropAt(now)).isNull();
        }

        @Test
        void 마지막_단계_직전에는_마지막_가격을_다음_가격으로_알려준다() {
            DutchPricePolicy policy = 기본_정책();
            LocalDateTime now = START_AT.plusMinutes(24).plusSeconds(30);

            assertThat(policy.isLastPriceAt(now)).isFalse();
            assertThat(policy.nextPriceAt(now)).isEqualTo(150_000L);
            assertThat(policy.nextDropAt(now)).isEqualTo(START_AT.plusMinutes(25));
        }
    }

    @Nested
    class 잘못된_조건 {

        @ParameterizedTest(name = "시작가 {0}, 최저가 {1}")
        @CsvSource({"300000, 300000", "300000, 400000", "300000, -1"})
        void 최저가는_0_이상이고_시작가보다_작아야_한다(long startPrice, long floorPrice) {
            assertThatThrownBy(() -> DutchPricePolicy.of(startPrice, floorPrice, 2, 60, START_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest(name = "하락률 {0}%, 주기 {1}초")
        @CsvSource({"0, 60", "-1, 60", "2, 0", "2, -30"})
        void 하락률과_주기는_0보다_커야_한다(int ratePercent, int intervalSec) {
            assertThatThrownBy(() -> DutchPricePolicy.of(300_000L, 150_000L, ratePercent, intervalSec, START_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 시작_시각이_없으면_만들_수_없다() {
            assertThatThrownBy(() -> DutchPricePolicy.of(300_000L, 150_000L, 2, 60, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
