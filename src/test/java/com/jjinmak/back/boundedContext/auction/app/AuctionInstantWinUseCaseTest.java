package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.Bid;
import com.jjinmak.back.boundedContext.auction.exception.AuctionErrorCode;
import com.jjinmak.back.boundedContext.auction.out.AuctionEventPublisher;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import com.jjinmak.back.boundedContext.auction.out.BidRepository;
import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.jjinmak.back.boundedContext.auction.domain.AuctionStatus.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuctionInstantWinUseCaseTest {

    private static final Long AUCTION_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long BIDDER_ID = 200L;
    private static final Long OTHER_BIDDER_ID = 300L;
    private static final Long INSTANT_WIN_PRICE = 50_000L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 20, 0);   // 요청 시각
    private static final LocalDateTime END_AT = NOW.plusDays(1);

    @Mock AuctionRepository auctionRepository;
    @Mock BidRepository bidRepository;
    @Mock AuctionEventPublisher auctionEventPublisher;

    @InjectMocks AuctionInstantWinUseCase auctionInstantWinUseCase;

    private Auction 경매(Long instantWinPrice, LocalDateTime endAt) {
        return Auction.create(1L, SELLER_ID, 10_000L, instantWinPrice, 3_000L, endAt, NOW.minusDays(3));
    }

    private Auction 즉시낙찰_가능_경매() {
        return 경매(INSTANT_WIN_PRICE, END_AT);
    }

    private void 경매가_조회된다(Auction auction) {
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
    }

    private void 입찰이_저장된다() {
        given(bidRepository.save(any(Bid.class))).willAnswer(inv -> inv.getArgument(0));
    }

    private void 즉시낙찰이_거부된다(Long bidderId, AuctionErrorCode expected) {
        assertThatThrownBy(() -> auctionInstantWinUseCase.instantWin(AUCTION_ID, bidderId, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
        then(bidRepository).shouldHaveNoInteractions();
        then(auctionEventPublisher).shouldHaveNoInteractions();
    }

    @Nested
    class 즉시낙찰_성공 {

        @Test
        void 즉시낙찰가로_낙찰되고_낙찰_이벤트가_발행된다() {
            // given
            Auction auction = 즉시낙찰_가능_경매();
            경매가_조회된다(auction);
            입찰이_저장된다();

            // when
            var response = auctionInstantWinUseCase.instantWin(AUCTION_ID, BIDDER_ID, NOW);

            // then
            assertThat(response.auctionId()).isEqualTo(AUCTION_ID);
            assertThat(response.winningPrice()).isEqualTo(INSTANT_WIN_PRICE);
            assertThat(response.winAt()).isEqualTo(NOW);
            assertThat(response.status()).isEqualTo(WON);
            assertThat(auction.getWinnerId()).isEqualTo(BIDDER_ID);
            assertThat(auction.getWinAt()).isEqualTo(NOW);
            then(auctionEventPublisher).should().publishWon(auction);
            then(auctionEventPublisher).should(never()).publishBidUpdated(any());
        }

        @Test
        void 즉시낙찰가로_입찰_기록이_저장된다() {
            경매가_조회된다(즉시낙찰_가능_경매());
            입찰이_저장된다();
            ArgumentCaptor<Bid> captor = ArgumentCaptor.forClass(Bid.class);

            auctionInstantWinUseCase.instantWin(AUCTION_ID, BIDDER_ID, NOW);

            then(bidRepository).should().save(captor.capture());
            assertThat(captor.getValue().getBidderId()).isEqualTo(BIDDER_ID);
            assertThat(captor.getValue().getBidPrice()).isEqualTo(INSTANT_WIN_PRICE);
            assertThat(captor.getValue().getBidAt()).isEqualTo(NOW);
        }

        @Test
        void 현재_최고_입찰자도_즉시낙찰할_수_있다() {
            Auction auction = 즉시낙찰_가능_경매();
            auction.updateHighestBid(BIDDER_ID, 20_000L);
            경매가_조회된다(auction);
            입찰이_저장된다();

            auctionInstantWinUseCase.instantWin(AUCTION_ID, BIDDER_ID, NOW);

            assertThat(auction.getStatus()).isEqualTo(WON);
        }

        @Test
        void 다른_사람의_입찰이_있어도_즉시낙찰가로_낙찰된다() {
            Auction auction = 즉시낙찰_가능_경매();
            auction.updateHighestBid(OTHER_BIDDER_ID, 49_000L);
            경매가_조회된다(auction);
            입찰이_저장된다();

            auctionInstantWinUseCase.instantWin(AUCTION_ID, BIDDER_ID, NOW);

            assertThat(auction.getWinnerId()).isEqualTo(BIDDER_ID);
            assertThat(auction.getWinningPrice()).isEqualTo(INSTANT_WIN_PRICE);
        }

        @Test
        void 종료_시각_1초_전에는_즉시낙찰할_수_있다() {
            Auction auction = 경매(INSTANT_WIN_PRICE, NOW.plusSeconds(1));
            경매가_조회된다(auction);
            입찰이_저장된다();

            auctionInstantWinUseCase.instantWin(AUCTION_ID, BIDDER_ID, NOW);

            assertThat(auction.getStatus()).isEqualTo(WON);
        }
    }

    @Nested
    class 즉시낙찰_거부 {

        @Test
        void 경매가_없으면_즉시낙찰할_수_없다() {
            given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.empty());

            즉시낙찰이_거부된다(BIDDER_ID, AuctionErrorCode.AUCTION_NOT_FOUND);
        }

        @Test
        void 진행중이_아닌_경매는_즉시낙찰할_수_없다() {
            Auction auction = 즉시낙찰_가능_경매();
            auction.win(OTHER_BIDDER_ID, 20_000L, NOW);
            경매가_조회된다(auction);

            즉시낙찰이_거부된다(BIDDER_ID, AuctionErrorCode.AUCTION_NOT_IN_PROGRESS);
        }

        @Test
        void 종료_시각과_같은_시각에는_즉시낙찰할_수_없다() {
            경매가_조회된다(경매(INSTANT_WIN_PRICE, NOW));

            즉시낙찰이_거부된다(BIDDER_ID, AuctionErrorCode.AUCTION_ALREADY_ENDED);
        }

        @Test
        void 종료_시각이_지나면_즉시낙찰할_수_없다() {
            경매가_조회된다(경매(INSTANT_WIN_PRICE, NOW.minusSeconds(1)));

            즉시낙찰이_거부된다(BIDDER_ID, AuctionErrorCode.AUCTION_ALREADY_ENDED);
        }

        @Test
        void 판매자는_즉시낙찰할_수_없다() {
            경매가_조회된다(즉시낙찰_가능_경매());

            즉시낙찰이_거부된다(SELLER_ID, AuctionErrorCode.SELLER_CANNOT_BID);
        }

        @Test
        void 즉시낙찰가가_없는_경매는_즉시낙찰할_수_없다() {
            경매가_조회된다(경매(null, END_AT));

            즉시낙찰이_거부된다(BIDDER_ID, AuctionErrorCode.INSTANT_WIN_NOT_AVAILABLE);
        }

        @Test
        void 이미_즉시낙찰된_경매에_다시_요청하면_거부되고_이벤트는_한_번만_발행된다() {
            Auction auction = 즉시낙찰_가능_경매();
            경매가_조회된다(auction);
            입찰이_저장된다();
            auctionInstantWinUseCase.instantWin(AUCTION_ID, BIDDER_ID, NOW);

            assertThatThrownBy(() -> auctionInstantWinUseCase.instantWin(AUCTION_ID, OTHER_BIDDER_ID, NOW))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuctionErrorCode.AUCTION_NOT_IN_PROGRESS);
            assertThat(auction.getWinnerId()).isEqualTo(BIDDER_ID);
            then(auctionEventPublisher).should(times(1)).publishWon(auction);
        }
    }
}