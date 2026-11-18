package com.jjinmak.back.boundedContext.auction.domain;

import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.auction.domain.AuctionStatus.*;
import static com.jjinmak.back.boundedContext.auction.domain.FailureReason.*;
import static com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode.INVALID_STATUS_TRANSITION;
import static org.assertj.core.api.Assertions.*;

class AuctionTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long BIDDER_ID = 200L;
    private static final Long OTHER_ID = 300L;
    private static final Long START_PRICE = 10_000L;
    private static final Long INSTANT_WIN_PRICE = 50_000L;
    private static final Long SHIPPING_FEE = 3_000L;
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 10, 17, 21, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 10, 20, 21, 0);
    private static final ProductSnapshot SNAPSHOT = ProductSnapshot.of("테스트 상품", "CONSOLE", null);

    // 상태별 경매는 실제 메서드 순서대로 만든다
    private Auction 진행중_경매() {
        return Auction.create(PRODUCT_ID, SELLER_ID, START_PRICE, INSTANT_WIN_PRICE,
                SHIPPING_FEE, SNAPSHOT, END_AT, START_AT);
    }

    private Auction 낙찰된_경매() {
        Auction auction = 진행중_경매();
        auction.updateHighestBid(BIDDER_ID, 15_000L);
        auction.win(BIDDER_ID, 15_000L, END_AT);
        return auction;
    }

    private Auction 결제된_경매() {
        Auction auction = 낙찰된_경매();
        auction.pay();
        return auction;
    }

    private void 상태전이_실패를_확인한다(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(INVALID_STATUS_TRANSITION);
    }

    @Nested
    class 낙찰 {

        @Test
        void 진행중인_경매가_낙찰되면_WON이_되고_낙찰정보가_기록된다() {
            // given
            Auction auction = 진행중_경매();

            // when
            auction.win(BIDDER_ID, 15_000L, END_AT);

            // then
            assertThat(auction.getStatus()).isEqualTo(WON);
            assertThat(auction.getWinnerId()).isEqualTo(BIDDER_ID);
            assertThat(auction.getWinningPrice()).isEqualTo(15_000L);
            assertThat(auction.getWinAt()).isEqualTo(END_AT);
        }

        @Test
        void 진행중이_아닌_경매는_낙찰할_수_없다() {
            Auction auction = 낙찰된_경매();

            상태전이_실패를_확인한다(() -> auction.win(OTHER_ID, 20_000L, END_AT));
        }
    }

    @Nested
    class 즉시낙찰 {

        @Test
        void 즉시낙찰가로_낙찰되고_입찰수가_1_증가한다() {
            Auction auction = 진행중_경매();

            auction.winInstantly(BIDDER_ID, START_AT.plusHours(1));

            assertThat(auction.getStatus()).isEqualTo(WON);
            assertThat(auction.getWinnerId()).isEqualTo(BIDDER_ID);
            assertThat(auction.getWinningPrice()).isEqualTo(INSTANT_WIN_PRICE);
            assertThat(auction.getHighestBidPrice()).isEqualTo(INSTANT_WIN_PRICE);
            assertThat(auction.getBidCount()).isEqualTo(1);
        }
    }

    @Nested
    class 결제완료 {

        @Test
        void WON_경매는_PAID가_된다() {
            Auction auction = 낙찰된_경매();

            auction.pay();

            assertThat(auction.getStatus()).isEqualTo(PAID);
        }

        @Test
        void WON이_아닌_경매는_결제완료할_수_없다() {
            Auction auction = 진행중_경매();

            상태전이_실패를_확인한다(auction::pay);
        }
    }

    @Nested
    class 경매완료 {

        @Test
        void PAID_경매는_COMPLETED가_된다() {
            Auction auction = 결제된_경매();

            auction.complete();

            assertThat(auction.getStatus()).isEqualTo(COMPLETED);
        }

        @Test
        void PAID가_아닌_경매는_완료할_수_없다() {
            Auction auction = 낙찰된_경매();

            상태전이_실패를_확인한다(auction::complete);
        }
    }

    @Nested
    class 무산 {

        @Test
        void 유찰은_진행중_경매에서_가능하고_사유가_기록된다() {
            Auction auction = 진행중_경매();

            auction.fail(NO_BID);

            assertThat(auction.getStatus()).isEqualTo(FAILED);
            assertThat(auction.getFailureReason()).isEqualTo(NO_BID);
        }

        @Test
        void 미결제는_WON_경매에서_가능하고_사유가_기록된다() {
            Auction auction = 낙찰된_경매();

            auction.fail(UNPAID);

            assertThat(auction.getStatus()).isEqualTo(FAILED);
            assertThat(auction.getFailureReason()).isEqualTo(UNPAID);
        }

        @Test
        void 환불은_PAID_경매에서_가능하고_사유가_기록된다() {
            Auction auction = 결제된_경매();

            auction.fail(REFUNDED);

            assertThat(auction.getStatus()).isEqualTo(FAILED);
            assertThat(auction.getFailureReason()).isEqualTo(REFUNDED);
        }

        @Test
        void 결제된_경매는_미결제로_무산할_수_없다() {
            Auction auction = 결제된_경매();

            상태전이_실패를_확인한다(() -> auction.fail(UNPAID));
        }

        @Test
        void 낙찰된_경매는_유찰될_수_없다() {
            Auction auction = 낙찰된_경매();

            상태전이_실패를_확인한다(() -> auction.fail(NO_BID));
        }
    }

    @Nested
    class 입찰_갱신 {

        @Test
        void 입찰하면_최고가와_최고입찰자가_바뀌고_입찰수가_1_증가한다() {
            Auction auction = 진행중_경매();

            auction.updateHighestBid(BIDDER_ID, 11_000L);

            assertThat(auction.getHighestBidPrice()).isEqualTo(11_000L);
            assertThat(auction.getHighestBidderId()).isEqualTo(BIDDER_ID);
            assertThat(auction.getBidCount()).isEqualTo(1);
        }
    }

    @Nested
    class 현재가와_최소입찰가 {

        @Test
        void 입찰이_없으면_현재가와_최소입찰가는_시작가다() {
            Auction auction = 진행중_경매();

            assertThat(auction.getCurrentPrice()).isEqualTo(START_PRICE);
            assertThat(auction.getMinBidPrice()).isEqualTo(START_PRICE);
        }

        @Test
        void 입찰이_있으면_최소입찰가는_최고가_더하기_입찰단위다() {
            Auction auction = 진행중_경매();
            auction.updateHighestBid(BIDDER_ID, 15_000L);   // 1만원 이상 구간 → 단위 1,000원

            assertThat(auction.getCurrentPrice()).isEqualTo(15_000L);
            assertThat(auction.getMinBidPrice()).isEqualTo(16_000L);
        }
    }

    @Nested
    class 종료_여부 {

        @Test
        void 종료시각_직전이면_종료되지_않았다() {
            assertThat(진행중_경매().isEnded(END_AT.minusSeconds(1))).isFalse();
        }

        @Test
        void 종료시각과_같으면_종료된_것이다() {
            assertThat(진행중_경매().isEnded(END_AT)).isTrue();
        }
    }

    @Nested
    class 판매자와_최고입찰자 {

        @Test
        void 판매자_id면_판매자다() {
            Auction auction = 진행중_경매();

            assertThat(auction.isSeller(SELLER_ID)).isTrue();
            assertThat(auction.isSeller(BIDDER_ID)).isFalse();
        }

        @Test
        void 입찰이_없으면_누구도_최고입찰자가_아니다() {
            assertThat(진행중_경매().isHighestBidder(BIDDER_ID)).isFalse();
        }
    }
}