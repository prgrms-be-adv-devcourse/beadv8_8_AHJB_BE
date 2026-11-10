package com.jjinmak.auction.domain;

import com.jjinmak.auction.exception.AuctionErrorCode;
import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDateTime;

import static com.jjinmak.auction.domain.AuctionStatus.*;
import static com.jjinmak.auction.exception.AuctionErrorCode.*;
import static org.assertj.core.api.Assertions.*;

class DutchAuctionTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long BUYER_ID = 200L;
    private static final Long OTHER_ID = 300L;
    private static final Long SHIPPING_FEE = 3_000L;
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 11, 9, 21, 0);
    private static final String CATEGORY = "CONSOLE";

    private DutchAuction 진행중_경매() {
        return DutchAuction.create(PRODUCT_ID, SELLER_ID, 300_000L, 150_000L, 2, 60,
                SHIPPING_FEE, CATEGORY, START_AT);
    }

    private void 에러코드를_확인한다(Executable action, AuctionErrorCode errorCode) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    @Nested
    class 생성 {

        @Test
        void 공지_시각에_시작하고_종료_시각은_가격_곡선으로_계산된다() {
            DutchAuction auction = 진행중_경매();

            assertThat(auction.getStatus()).isEqualTo(IN_PROGRESS);
            assertThat(auction.getStartAt()).isEqualTo(START_AT);
            assertThat(auction.getEndAt()).isEqualTo(START_AT.plusMinutes(26));
            assertThat(auction.getDropAmount()).isEqualTo(6_000L);
        }

        @Test
        void 하향식_조건이_잘못되면_생성할_수_없다() {
            assertThatThrownBy(() -> DutchAuction.create(PRODUCT_ID, SELLER_ID, 300_000L, 300_000L, 2, 60,
                    SHIPPING_FEE, CATEGORY, START_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 가격_조회 {

        @Test
        void 현재가와_다음_가격과_다음_하락_시각을_계산한다() {
            DutchAuction auction = 진행중_경매();
            LocalDateTime now = START_AT.plusMinutes(10).plusSeconds(30);

            assertThat(auction.currentPriceAt(now)).isEqualTo(240_000L);
            assertThat(auction.nextPriceAt(now)).isEqualTo(234_000L);
            assertThat(auction.nextDropAt(now)).isEqualTo(START_AT.plusMinutes(11));
            assertThat(auction.isLastPriceAt(now)).isFalse();
        }

        @Test
        void 마지막_단계에서는_다음_가격과_다음_하락_시각이_없다() {
            DutchAuction auction = 진행중_경매();
            LocalDateTime now = START_AT.plusMinutes(25);

            assertThat(auction.currentPriceAt(now)).isEqualTo(150_000L);
            assertThat(auction.nextPriceAt(now)).isNull();
            assertThat(auction.nextDropAt(now)).isNull();
            assertThat(auction.isLastPriceAt(now)).isTrue();
        }
    }

    @Nested
    class 구매 {

        @Test
        void 현재가로_구매하면_낙찰된다() {
            DutchAuction auction = 진행중_경매();
            LocalDateTime now = START_AT.plusMinutes(5);   // 270,000원

            auction.buy(BUYER_ID, 270_000L, now);

            assertThat(auction.getStatus()).isEqualTo(WON);
            assertThat(auction.getWinnerId()).isEqualTo(BUYER_ID);
            assertThat(auction.getWinningPrice()).isEqualTo(270_000L);
            assertThat(auction.getWinAt()).isEqualTo(now);
        }

        @Test
        void 본_가격보다_서버_가격이_낮아졌으면_낮은_가격으로_낙찰된다() {
            DutchAuction auction = 진행중_경매();
            // 회원은 21:04에 276,000원을 보고 눌렀지만 요청은 21:05에 도착
            auction.buy(BUYER_ID, 276_000L, START_AT.plusMinutes(5));

            assertThat(auction.getWinningPrice()).isEqualTo(270_000L);
        }

        @Test
        void 서버_가격이_본_가격보다_높으면_구매할_수_없다() {
            DutchAuction auction = 진행중_경매();
            // 시계 오차로 회원은 다음 가격을 먼저 봄
            에러코드를_확인한다(() -> auction.buy(BUYER_ID, 264_000L, START_AT.plusMinutes(5)), DUTCH_PRICE_CHANGED);
            assertThat(auction.getStatus()).isEqualTo(IN_PROGRESS);
        }

        @Test
        void 기대_가격이_없으면_구매할_수_없다() {
            DutchAuction auction = 진행중_경매();

            에러코드를_확인한다(() -> auction.buy(BUYER_ID, null, START_AT), DUTCH_PRICE_CHANGED);
        }

        @Test
        void 마지막_단계의_최저가로_구매할_수_있다() {
            DutchAuction auction = 진행중_경매();

            auction.buy(BUYER_ID, 150_000L, START_AT.plusMinutes(25).plusSeconds(59));

            assertThat(auction.getWinningPrice()).isEqualTo(150_000L);
        }

        @Test
        void 종료_시각_정각부터는_구매할_수_없다() {
            DutchAuction auction = 진행중_경매();

            에러코드를_확인한다(() -> auction.buy(BUYER_ID, 150_000L, START_AT.plusMinutes(26)), AUCTION_ALREADY_ENDED);
        }

        @Test
        void 이미_낙찰된_경매는_다시_구매할_수_없다() {
            DutchAuction auction = 진행중_경매();
            auction.buy(BUYER_ID, 300_000L, START_AT);

            에러코드를_확인한다(() -> auction.buy(OTHER_ID, 300_000L, START_AT.plusSeconds(1)), INVALID_STATUS_TRANSITION);
            assertThat(auction.getWinnerId()).isEqualTo(BUYER_ID);
        }
    }

    @Nested
    class 종료처리 {

        @Test
        void 아무도_사지_않고_종료되면_유찰된다() {
            DutchAuction auction = 진행중_경매();

            AuctionCloseResult result = auction.close(START_AT.plusMinutes(26));

            assertThat(result).isEqualTo(AuctionCloseResult.FAILED);
            assertThat(auction.getStatus()).isEqualTo(FAILED);
            assertThat(auction.getFailureReason()).isEqualTo(FailureReason.NO_BID);
        }

        @Test
        void 낙찰된_경매는_유찰_처리할_수_없다() {
            DutchAuction auction = 진행중_경매();
            auction.buy(BUYER_ID, 300_000L, START_AT);

            에러코드를_확인한다(() -> auction.close(START_AT.plusMinutes(26)), INVALID_STATUS_TRANSITION);
        }
    }
}