package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode;
import com.jjinmak.back.boundedContext.auction.out.AuctionEventPublisher;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.jjinmak.back.boundedContext.auction.domain.AuctionStatus.*;
import static com.jjinmak.back.boundedContext.auction.domain.FailureReason.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuctionTradeResultUseCaseTest {

    private static final Long AUCTION_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long WINNER_ID = 200L;
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 10, 20, 21, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 0);

    @Mock AuctionRepository auctionRepository;
    @Mock AuctionEventPublisher auctionEventPublisher;

    @InjectMocks AuctionTradeResultUseCase auctionTradeResultUseCase;

    // ── 상태별 경매 ──
    private Auction 진행중_경매() {
        return Auction.create(1L, SELLER_ID, 10_000L, null, 3_000L, END_AT, END_AT.minusDays(3));
    }

    private Auction 낙찰_경매() {
        Auction auction = 진행중_경매();
        auction.updateHighestBid(WINNER_ID, 20_000L);
        auction.win(WINNER_ID, 20_000L, END_AT);
        return auction;
    }

    private Auction 결제완료_경매() {
        Auction auction = 낙찰_경매();
        auction.pay();
        return auction;
    }

    private Auction 구매확정_경매() {
        Auction auction = 결제완료_경매();
        auction.complete();
        return auction;
    }

    private void 경매가_조회된다(Auction auction) {
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
    }

    private void 예외가_발생한다(Runnable action, AuctionErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
        then(auctionEventPublisher).shouldHaveNoInteractions();
    }

    @Nested
    class 결제완료 {

        @Test
        void 낙찰_경매는_결제완료가_되고_이벤트는_발행하지_않는다() {
            // given
            Auction auction = 낙찰_경매();
            경매가_조회된다(auction);

            // when
            auctionTradeResultUseCase.applyPaymentSucceeded(AUCTION_ID);

            // then
            assertThat(auction.getStatus()).isEqualTo(PAID);
            then(auctionEventPublisher).shouldHaveNoInteractions();
        }

        @Test
        void 이미_결제완료면_중복으로_보고_무시한다() {
            Auction auction = 결제완료_경매();
            경매가_조회된다(auction);

            auctionTradeResultUseCase.applyPaymentSucceeded(AUCTION_ID);

            assertThat(auction.getStatus()).isEqualTo(PAID);
        }

        @Test
        void 진행중_경매는_결제완료할_수_없다() {
            경매가_조회된다(진행중_경매());

            예외가_발생한다(() -> auctionTradeResultUseCase.applyPaymentSucceeded(AUCTION_ID),
                    AuctionErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        void 미결제로_무산된_경매에_결제완료가_오면_예외가_발생한다() {
            // 돈은 결제됐는데 경매는 무산된 상태 → 조용히 무시하면 안 된다
            Auction auction = 낙찰_경매();
            auction.fail(UNPAID);
            경매가_조회된다(auction);

            예외가_발생한다(() -> auctionTradeResultUseCase.applyPaymentSucceeded(AUCTION_ID),
                    AuctionErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        void 경매가_없으면_예외가_발생한다() {
            given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.empty());

            예외가_발생한다(() -> auctionTradeResultUseCase.applyPaymentSucceeded(AUCTION_ID),
                    AuctionErrorCode.AUCTION_NOT_FOUND);
        }
    }

    @Nested
    class 구매확정 {

        @Test
        void 결제완료_경매는_경매완료가_된다() {
            Auction auction = 결제완료_경매();
            경매가_조회된다(auction);

            auctionTradeResultUseCase.applyPurchaseConfirmed(AUCTION_ID);

            assertThat(auction.getStatus()).isEqualTo(COMPLETED);
            then(auctionEventPublisher).shouldHaveNoInteractions();
        }

        @Test
        void 이미_경매완료면_중복으로_보고_무시한다() {
            Auction auction = 구매확정_경매();
            경매가_조회된다(auction);

            auctionTradeResultUseCase.applyPurchaseConfirmed(AUCTION_ID);

            assertThat(auction.getStatus()).isEqualTo(COMPLETED);
        }

        @Test
        void 결제_전인_낙찰_경매는_구매확정할_수_없다() {
            // 결제완료 이벤트가 유실된 상황
            경매가_조회된다(낙찰_경매());

            예외가_발생한다(() -> auctionTradeResultUseCase.applyPurchaseConfirmed(AUCTION_ID),
                    AuctionErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    @Nested
    class 미결제 {

        @Test
        void 낙찰_경매는_미결제로_무산되고_무산_이벤트가_발행된다() {
            Auction auction = 낙찰_경매();
            경매가_조회된다(auction);

            auctionTradeResultUseCase.applyPaymentDefaulted(AUCTION_ID, NOW);

            assertThat(auction.getStatus()).isEqualTo(FAILED);
            assertThat(auction.getFailureReason()).isEqualTo(UNPAID);
            then(auctionEventPublisher).should().publishFailed(auction, NOW);
        }

        @Test
        void 이미_무산됐으면_중복으로_보고_이벤트를_다시_발행하지_않는다() {
            Auction auction = 낙찰_경매();
            경매가_조회된다(auction);

            auctionTradeResultUseCase.applyPaymentDefaulted(AUCTION_ID, NOW);
            auctionTradeResultUseCase.applyPaymentDefaulted(AUCTION_ID, NOW);

            then(auctionEventPublisher).should(times(1)).publishFailed(auction, NOW);
        }

        @Test
        void 결제완료된_경매에_미결제가_오면_예외가_발생한다() {
            // 결제 직후 결제 기한 만료가 겹친 경합 상황
            Auction auction = 결제완료_경매();
            경매가_조회된다(auction);

            예외가_발생한다(() -> auctionTradeResultUseCase.applyPaymentDefaulted(AUCTION_ID, NOW),
                    AuctionErrorCode.INVALID_STATUS_TRANSITION);
            assertThat(auction.getStatus()).isEqualTo(PAID);
        }
    }

    @Nested
    class 환불완료 {

        @Test
        void 결제완료_경매는_환불로_무산되고_무산_이벤트가_발행된다() {
            Auction auction = 결제완료_경매();
            경매가_조회된다(auction);

            auctionTradeResultUseCase.applyRefundAccepted(AUCTION_ID, NOW);

            assertThat(auction.getStatus()).isEqualTo(FAILED);
            assertThat(auction.getFailureReason()).isEqualTo(REFUNDED);
            then(auctionEventPublisher).should().publishFailed(auction, NOW);
        }

        @Test
        void 이미_무산됐으면_중복으로_보고_무시한다() {
            Auction auction = 결제완료_경매();
            auction.fail(REFUNDED);
            경매가_조회된다(auction);

            auctionTradeResultUseCase.applyRefundAccepted(AUCTION_ID, NOW);

            then(auctionEventPublisher).shouldHaveNoInteractions();
        }

        @Test
        void 결제_전인_낙찰_경매는_환불될_수_없다() {
            경매가_조회된다(낙찰_경매());

            예외가_발생한다(() -> auctionTradeResultUseCase.applyRefundAccepted(AUCTION_ID, NOW),
                    AuctionErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        void 경매완료된_경매는_환불될_수_없다() {
            경매가_조회된다(구매확정_경매());

            예외가_발생한다(() -> auctionTradeResultUseCase.applyRefundAccepted(AUCTION_ID, NOW),
                    AuctionErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    @Nested
    class 연속_수신 {

        @Test
        void 결제완료_후_환불되면_무산되고_이후_구매확정은_예외가_발생한다() {
            Auction auction = 낙찰_경매();
            경매가_조회된다(auction);

            auctionTradeResultUseCase.applyPaymentSucceeded(AUCTION_ID);
            auctionTradeResultUseCase.applyRefundAccepted(AUCTION_ID, NOW);

            assertThatThrownBy(() -> auctionTradeResultUseCase.applyPurchaseConfirmed(AUCTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuctionErrorCode.INVALID_STATUS_TRANSITION);
            assertThat(auction.getFailureReason()).isEqualTo(REFUNDED);
        }
    }

    /**
     * 알려진 한계: 무산은 결과 상태(FAILED)만 비교하므로, 다른 사유로 무산된 경매에 무산 이벤트가 와도 중복으로 보고 무시한다.
     * 실제로는 일어나면 안 되는 조합이다. failureReason까지 비교할지 결정되면 이 테스트를 예외 기대로 바꾼다.
     */
    @Nested
    class 다른_사유로_무산된_경매 {

        @Test
        void 유찰된_경매에_미결제가_와도_현재는_무시한다() {
            Auction auction = 진행중_경매();
            auction.fail(NO_BID);
            경매가_조회된다(auction);

            auctionTradeResultUseCase.applyPaymentDefaulted(AUCTION_ID, NOW);

            assertThat(auction.getFailureReason()).isEqualTo(NO_BID);
            then(auctionEventPublisher).shouldHaveNoInteractions();
        }
    }
}