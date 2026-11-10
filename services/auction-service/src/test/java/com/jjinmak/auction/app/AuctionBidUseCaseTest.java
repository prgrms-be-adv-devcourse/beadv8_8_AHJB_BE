package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.domain.Bid;
import com.jjinmak.auction.exception.AuctionErrorCode;
import com.jjinmak.auction.out.AuctionRepository;
import com.jjinmak.auction.out.BidRepository;
import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.jjinmak.auction.domain.AuctionStatus.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuctionBidUseCaseTest {

    private static final Long AUCTION_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long BIDDER_ID = 200L;
    private static final Long OTHER_BIDDER_ID = 300L;
    private static final Long START_PRICE = 10_000L;
    private static final Long INSTANT_WIN_PRICE = 50_000L;
    private static final Long SHIPPING_FEE = 3_000L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 20, 0);   // 입찰 시각
    private static final LocalDateTime END_AT = NOW.plusDays(1);
    private static final String CATEGORY = "CONSOLE";

    @Mock AuctionRepository auctionRepository;
    @Mock BidRepository bidRepository;
    @Mock AuctionEventPublisher auctionEventPublisher;

    AuctionBidUseCase auctionBidUseCase;

    // AuctionReader는 mock이 아니라 실제 객체로 조립해 findById mock을 그대로 쓴다
    @BeforeEach
    void setUp() {
        auctionBidUseCase = new AuctionBidUseCase(
                bidRepository, auctionEventPublisher, new AuctionReader(auctionRepository));
    }

    // ── 경매 만들기 ──
    private EnglishAuction 경매(Long instantWinPrice, LocalDateTime endAt) {
        return EnglishAuction.create(1L, SELLER_ID, START_PRICE, instantWinPrice, SHIPPING_FEE, CATEGORY, endAt, NOW.minusDays(3));
    }

    private EnglishAuction 진행중_경매() {
        return 경매(null, END_AT);
    }

    private EnglishAuction 즉시낙찰가_있는_경매() {
        return 경매(INSTANT_WIN_PRICE, END_AT);
    }

    private EnglishAuction 입찰_있는_경매(Long bidderId, Long price) {
        EnglishAuction auction = 진행중_경매();
        auction.updateHighestBid(bidderId, price);
        return auction;
    }

    // ── Mock 설정 ──
    private void 경매가_조회된다(EnglishAuction auction) {
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
    }

    private void 입찰이_저장된다() {
        // Mock은 기본으로 null을 돌려주므로, 받은 Bid를 그대로 돌려주게 한다
        given(bidRepository.save(any(Bid.class))).willAnswer(inv -> inv.getArgument(0));
    }

    private void 입찰이_거부된다(Long bidderId, Long bidPrice, AuctionErrorCode expected) {
        assertThatThrownBy(() -> auctionBidUseCase.bid(AUCTION_ID, bidderId, bidPrice, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
        then(bidRepository).shouldHaveNoInteractions();
        then(auctionEventPublisher).shouldHaveNoInteractions();
    }

    @Nested
    class 정상_입찰 {

        @Test
        void 입찰이_없으면_시작가로_입찰할_수_있다() {
            // given
            EnglishAuction auction = 진행중_경매();
            경매가_조회된다(auction);
            입찰이_저장된다();

            // when
            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 10_000L, NOW);

            // then
            assertThat(response.bidPrice()).isEqualTo(10_000L);
            assertThat(response.currentPrice()).isEqualTo(10_000L);
            assertThat(response.minBidPrice()).isEqualTo(11_000L);
            assertThat(response.isInstantWin()).isFalse();
            assertThat(response.isExtended()).isFalse();
            assertThat(auction.getHighestBidderId()).isEqualTo(BIDDER_ID);
            assertThat(auction.getBidCount()).isEqualTo(1);
            then(auctionEventPublisher).should(never()).publishWon(any());
        }

        @Test
        void 입찰이_있으면_현재가에_단위를_더한_가격부터_입찰할_수_있다() {
            EnglishAuction auction = 입찰_있는_경매(OTHER_BIDDER_ID, 15_000L);
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 16_000L, NOW);

            assertThat(response.currentPrice()).isEqualTo(16_000L);
            assertThat(response.minBidPrice()).isEqualTo(17_000L);
            assertThat(auction.getHighestBidderId()).isEqualTo(BIDDER_ID);
        }

        @Test
        void 단위의_배수만큼_더_높게_입찰할_수_있다() {
            EnglishAuction auction = 입찰_있는_경매(OTHER_BIDDER_ID, 15_000L);
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 20_000L, NOW);

            assertThat(response.currentPrice()).isEqualTo(20_000L);
        }

        @Test
        void 입찰로_가격_구간이_바뀌면_다음_최소입찰가에_새_단위가_적용된다() {
            // 99,000원(단위 1,000) → 100,000원 입찰 → 단위 5,000
            EnglishAuction auction = 입찰_있는_경매(OTHER_BIDDER_ID, 99_000L);
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 100_000L, NOW);

            assertThat(response.minBidPrice()).isEqualTo(105_000L);
        }

        @Test
        void 저장되는_입찰에는_입찰자와_입찰가와_입찰시각이_담긴다() {
            경매가_조회된다(진행중_경매());
            입찰이_저장된다();
            ArgumentCaptor<Bid> captor = ArgumentCaptor.forClass(Bid.class);

            auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 11_000L, NOW);

            then(bidRepository).should().save(captor.capture());
            Bid saved = captor.getValue();
            assertThat(saved.getBidderId()).isEqualTo(BIDDER_ID);
            assertThat(saved.getBidPrice()).isEqualTo(11_000L);
            assertThat(saved.getBidAt()).isEqualTo(NOW);
        }
    }

    @Nested
    class 입찰_거부 {

        @Test
        void 경매가_없으면_입찰할_수_없다() {
            given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.empty());

            입찰이_거부된다(BIDDER_ID, 10_000L, AuctionErrorCode.AUCTION_NOT_FOUND);
        }

        @Test
        void 하향식_경매에는_입찰할_수_없다() {
            DutchAuction dutch = DutchAuction.create(1L, SELLER_ID, 300_000L, 150_000L, 2, 60,
                    3_000L, CATEGORY, NOW.minusMinutes(1));
            given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(dutch));

            입찰이_거부된다(BIDDER_ID, 310_000L, AuctionErrorCode.AUCTION_TYPE_MISMATCH);
        }

        @Test
        void 진행중이_아닌_경매에는_입찰할_수_없다() {
            EnglishAuction auction = 입찰_있는_경매(OTHER_BIDDER_ID, 15_000L);
            auction.win(OTHER_BIDDER_ID, 15_000L, NOW);
            경매가_조회된다(auction);

            입찰이_거부된다(BIDDER_ID, 20_000L, AuctionErrorCode.AUCTION_NOT_IN_PROGRESS);
        }

        @Test
        void 판매자는_입찰할_수_없다() {
            경매가_조회된다(진행중_경매());

            입찰이_거부된다(SELLER_ID, 10_000L, AuctionErrorCode.SELLER_CANNOT_BID);
        }

        @Test
        void 현재_최고_입찰자는_재입찰할_수_없다() {
            경매가_조회된다(입찰_있는_경매(BIDDER_ID, 15_000L));

            입찰이_거부된다(BIDDER_ID, 16_000L, AuctionErrorCode.ALREADY_HIGHEST_BIDDER);
        }

        @Test
        void 입찰이_없을_때_시작가보다_낮으면_입찰할_수_없다() {
            경매가_조회된다(진행중_경매());

            입찰이_거부된다(BIDDER_ID, 9_500L, AuctionErrorCode.BID_PRICE_TOO_LOW);
        }

        @Test
        void 입찰이_있을_때_현재가와_같은_금액은_입찰할_수_없다() {
            경매가_조회된다(입찰_있는_경매(OTHER_BIDDER_ID, 15_000L));

            입찰이_거부된다(BIDDER_ID, 15_000L, AuctionErrorCode.BID_PRICE_TOO_LOW);
        }

        @Test
        void 입찰이_없을_때_단위가_맞지_않으면_입찰할_수_없다() {
            경매가_조회된다(진행중_경매());

            입찰이_거부된다(BIDDER_ID, 10_500L, AuctionErrorCode.INVALID_BID_UNIT);
        }

        @Test
        void 최소입찰가_이상이어도_단위가_맞지_않으면_입찰할_수_없다() {
            경매가_조회된다(입찰_있는_경매(OTHER_BIDDER_ID, 15_000L));

            입찰이_거부된다(BIDDER_ID, 16_500L, AuctionErrorCode.INVALID_BID_UNIT);
        }

        @Test
        void 거부된_입찰은_경매를_바꾸지_않는다() {
            EnglishAuction auction = 입찰_있는_경매(OTHER_BIDDER_ID, 15_000L);
            경매가_조회된다(auction);

            입찰이_거부된다(BIDDER_ID, 16_500L, AuctionErrorCode.INVALID_BID_UNIT);

            assertThat(auction.getCurrentPrice()).isEqualTo(15_000L);
            assertThat(auction.getHighestBidderId()).isEqualTo(OTHER_BIDDER_ID);
            assertThat(auction.getBidCount()).isEqualTo(1);
        }
    }

    @Nested
    class 종료_시각_경계 {

        @Test
        void 종료_시각_1초_전에는_입찰할_수_있다() {
            EnglishAuction auction = 경매(null, NOW.plusSeconds(1));
            경매가_조회된다(auction);
            입찰이_저장된다();

            auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 10_000L, NOW);

            assertThat(auction.getHighestBidderId()).isEqualTo(BIDDER_ID);
        }

        @Test
        void 종료_시각과_같은_시각에는_입찰할_수_없다() {
            경매가_조회된다(경매(null, NOW));

            입찰이_거부된다(BIDDER_ID, 10_000L, AuctionErrorCode.AUCTION_ALREADY_ENDED);
        }

        @Test
        void 종료_시각이_지나면_입찰할_수_없다() {
            경매가_조회된다(경매(null, NOW.minusSeconds(1)));

            입찰이_거부된다(BIDDER_ID, 10_000L, AuctionErrorCode.AUCTION_ALREADY_ENDED);
        }
    }

    @Nested
    class 경매_연장 {

        @Test
        void 종료_5분_전_이후에_입찰하면_입찰_시각_5분_뒤로_연장된다() {
            EnglishAuction auction = 경매(null, NOW.plusMinutes(3));
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 10_000L, NOW);

            assertThat(response.isExtended()).isTrue();
            assertThat(response.endAt()).isEqualTo(NOW.plusMinutes(5));
            assertThat(auction.getEndAt()).isEqualTo(NOW.plusMinutes(5));
        }

        @Test
        void 종료_정확히_5분_전에_입찰해도_연장된다() {
            // 정책: "종료 5분 전 이후"는 5분 전 정각을 포함한다
            EnglishAuction auction = 경매(null, NOW.plusMinutes(5));
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 10_000L, NOW);

            assertThat(response.isExtended()).isTrue();
            assertThat(auction.getEndAt()).isEqualTo(NOW.plusMinutes(5));
        }

        @Test
        void 종료_5분_1초_전에_입찰하면_연장되지_않는다() {
            LocalDateTime endAt = NOW.plusMinutes(5).plusSeconds(1);
            EnglishAuction auction = 경매(null, endAt);
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 10_000L, NOW);

            assertThat(response.isExtended()).isFalse();
            assertThat(auction.getEndAt()).isEqualTo(endAt);
        }

        @Test
        void 연장이_반복되면_마지막_입찰_시각_기준으로_5분_연장된다() {
            EnglishAuction auction = 경매(null, NOW.plusMinutes(3));
            경매가_조회된다(auction);
            입찰이_저장된다();

            auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 10_000L, NOW);                       // → NOW+5분
            auctionBidUseCase.bid(AUCTION_ID, OTHER_BIDDER_ID, 11_000L, NOW.plusMinutes(2));   // → NOW+7분

            assertThat(auction.getEndAt()).isEqualTo(NOW.plusMinutes(7));
        }
    }

    @Nested
    class 즉시낙찰가_이상_입찰 {

        @Test
        void 즉시낙찰가와_같으면_즉시낙찰되고_낙찰_이벤트가_발행된다() {
            EnglishAuction auction = 즉시낙찰가_있는_경매();
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, INSTANT_WIN_PRICE, NOW);

            assertThat(response.isInstantWin()).isTrue();
            assertThat(response.minBidPrice()).isNull();
            assertThat(auction.getStatus()).isEqualTo(WON);
            assertThat(auction.getWinnerId()).isEqualTo(BIDDER_ID);
            assertThat(auction.getWinningPrice()).isEqualTo(INSTANT_WIN_PRICE);
            then(auctionEventPublisher).should().publishWon(auction);
        }

        @Test
        void 즉시낙찰가보다_높게_입찰해도_즉시낙찰가로_낙찰되고_저장된다() {
            EnglishAuction auction = 즉시낙찰가_있는_경매();
            경매가_조회된다(auction);
            입찰이_저장된다();
            ArgumentCaptor<Bid> captor = ArgumentCaptor.forClass(Bid.class);

            auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 60_000L, NOW);

            assertThat(auction.getWinningPrice()).isEqualTo(INSTANT_WIN_PRICE);
            then(bidRepository).should().save(captor.capture());
            assertThat(captor.getValue().getBidPrice()).isEqualTo(INSTANT_WIN_PRICE);
        }

        @Test
        void 즉시낙찰이면_입찰_단위가_맞지_않아도_낙찰된다() {
            EnglishAuction auction = 즉시낙찰가_있는_경매();
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 50_500L, NOW);

            assertThat(response.isInstantWin()).isTrue();
            assertThat(auction.getStatus()).isEqualTo(WON);
        }

        @Test
        void 현재_최고_입찰자도_즉시낙찰가로_입찰할_수_있다() {
            EnglishAuction auction = 즉시낙찰가_있는_경매();
            auction.updateHighestBid(BIDDER_ID, 20_000L);
            경매가_조회된다(auction);
            입찰이_저장된다();

            auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, INSTANT_WIN_PRICE, NOW);

            assertThat(auction.getStatus()).isEqualTo(WON);
            assertThat(auction.getWinnerId()).isEqualTo(BIDDER_ID);
        }

        @Test
        void 즉시낙찰가_바로_아래는_일반_입찰로_처리된다() {
            EnglishAuction auction = 즉시낙찰가_있는_경매();
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 49_000L, NOW);

            assertThat(response.isInstantWin()).isFalse();
            assertThat(auction.getStatus()).isEqualTo(IN_PROGRESS);
        }

        @Test
        void 즉시낙찰가가_없으면_큰_금액도_일반_입찰로_처리된다() {
            EnglishAuction auction = 진행중_경매();
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 1_000_000L, NOW);

            assertThat(response.isInstantWin()).isFalse();
            assertThat(auction.getStatus()).isEqualTo(IN_PROGRESS);
        }

        @Test
        void 판매자는_즉시낙찰가로도_입찰할_수_없다() {
            경매가_조회된다(즉시낙찰가_있는_경매());

            입찰이_거부된다(SELLER_ID, INSTANT_WIN_PRICE, AuctionErrorCode.SELLER_CANNOT_BID);
        }

        @Test
        void 종료_임박_구간의_즉시낙찰은_연장하지_않는다() {
            LocalDateTime endAt = NOW.plusMinutes(3);
            EnglishAuction auction = 경매(INSTANT_WIN_PRICE, endAt);
            경매가_조회된다(auction);
            입찰이_저장된다();

            var response = auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, INSTANT_WIN_PRICE, NOW);

            assertThat(response.isExtended()).isFalse();
            assertThat(auction.getEndAt()).isEqualTo(endAt);
        }
    }

    @Nested
    class 입찰_성공_이벤트 {

        @Test
        void 일반_입찰이면_저장한_입찰로_입찰_성공_이벤트를_발행한다() {
            EnglishAuction auction = 진행중_경매();
            경매가_조회된다(auction);
            입찰이_저장된다();
            ArgumentCaptor<Bid> captor = ArgumentCaptor.forClass(Bid.class);

            auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, 10_000L, NOW);

            then(bidRepository).should().save(captor.capture());
            then(auctionEventPublisher).should().publishBidSucceed(auction, captor.getValue());
            then(auctionEventPublisher).should(never()).publishWon(any());
        }

        @Test
        void 즉시낙찰가_이상_입찰이면_입찰_성공_이벤트_다음에_낙찰_이벤트를_발행한다() {
            EnglishAuction auction = 즉시낙찰가_있는_경매();
            경매가_조회된다(auction);
            입찰이_저장된다();

            auctionBidUseCase.bid(AUCTION_ID, BIDDER_ID, INSTANT_WIN_PRICE, NOW);

            InOrder order = inOrder(auctionEventPublisher);
            order.verify(auctionEventPublisher).publishBidSucceed(any(), any());
            order.verify(auctionEventPublisher).publishWon(auction);
        }
    }
}
