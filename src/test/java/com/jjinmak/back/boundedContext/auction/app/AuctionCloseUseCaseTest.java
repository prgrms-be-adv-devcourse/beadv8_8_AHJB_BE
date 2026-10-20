package com.jjinmak.back.boundedContext.auction.app;

import com.jjinmak.back.boundedContext.auction.domain.Auction;
import com.jjinmak.back.boundedContext.auction.domain.ProductSnapshot;
import com.jjinmak.back.boundedContext.auction.domain.AuctionCloseResult;
import com.jjinmak.back.boundedContext.auction.out.AuctionEventPublisher;
import com.jjinmak.back.boundedContext.auction.out.AuctionRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.jjinmak.back.boundedContext.auction.domain.AuctionStatus.*;
import static com.jjinmak.back.boundedContext.auction.domain.FailureReason.NO_BID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuctionCloseUseCaseTest {

    private static final Long AUCTION_ID = 1L;
    private static final Long SELLER_ID = 100L;
    private static final Long BIDDER_ID = 200L;
    private static final Long HIGHEST_PRICE = 20_000L;
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 10, 20, 21, 0);
    private static final ProductSnapshot SNAPSHOT = ProductSnapshot.of("테스트 상품", "CONSOLE", null);

    @Mock AuctionRepository auctionRepository;
    @Mock AuctionEventPublisher auctionEventPublisher;

    @InjectMocks AuctionCloseUseCase auctionCloseUseCase;

    private Auction 입찰_없는_경매() {
        return Auction.create(1L, SELLER_ID, 10_000L, null, 3_000L, SNAPSHOT, END_AT, END_AT.minusDays(3));
    }

    private Auction 입찰_있는_경매() {
        Auction auction = 입찰_없는_경매();
        auction.updateHighestBid(BIDDER_ID, HIGHEST_PRICE);
        return auction;
    }

    private void 경매가_조회된다(Auction auction) {
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
    }

    @Nested
    class 낙찰 {

        @Test
        void 입찰이_있으면_최고_입찰자에게_낙찰되고_낙찰_이벤트가_발행된다() {
            // given
            Auction auction = 입찰_있는_경매();
            경매가_조회된다(auction);
            LocalDateTime now = END_AT.plusSeconds(1);

            // when
            AuctionCloseResult result = auctionCloseUseCase.closeAuction(AUCTION_ID, now);

            // then
            assertThat(result).isEqualTo(AuctionCloseResult.WON);
            assertThat(auction.getStatus()).isEqualTo(WON);
            assertThat(auction.getWinnerId()).isEqualTo(BIDDER_ID);
            assertThat(auction.getWinningPrice()).isEqualTo(HIGHEST_PRICE);
            assertThat(auction.getWinAt()).isEqualTo(now);
            then(auctionEventPublisher).should().publishWon(auction);
            then(auctionEventPublisher).should(never()).publishFailed(any(), any());
        }

        @Test
        void 종료_시각과_같은_시각에도_낙찰된다() {
            경매가_조회된다(입찰_있는_경매());

            assertThat(auctionCloseUseCase.closeAuction(AUCTION_ID, END_AT)).isEqualTo(AuctionCloseResult.WON);
        }
    }

    @Nested
    class 유찰 {

        @Test
        void 입찰이_없으면_유찰되고_무산_이벤트가_발행된다() {
            Auction auction = 입찰_없는_경매();
            경매가_조회된다(auction);
            LocalDateTime now = END_AT.plusSeconds(1);

            AuctionCloseResult result = auctionCloseUseCase.closeAuction(AUCTION_ID, now);

            assertThat(result).isEqualTo(AuctionCloseResult.FAILED);
            assertThat(auction.getStatus()).isEqualTo(FAILED);
            assertThat(auction.getFailureReason()).isEqualTo(NO_BID);
            then(auctionEventPublisher).should().publishFailed(auction, now);
            then(auctionEventPublisher).should(never()).publishWon(any());
        }
    }

    @Nested
    class 건너뜀 {

        @Test
        void 종료_시각_1초_전이면_건너뛴다() {
            Auction auction = 입찰_있는_경매();
            경매가_조회된다(auction);

            AuctionCloseResult result = auctionCloseUseCase.closeAuction(AUCTION_ID, END_AT.minusSeconds(1));

            assertThat(result).isEqualTo(AuctionCloseResult.SKIPPED);
            assertThat(auction.getStatus()).isEqualTo(IN_PROGRESS);
            then(auctionEventPublisher).shouldHaveNoInteractions();
        }

        @Test
        void 경매가_없으면_건너뛴다() {
            given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.empty());

            AuctionCloseResult result = auctionCloseUseCase.closeAuction(AUCTION_ID, END_AT.plusSeconds(1));

            assertThat(result).isEqualTo(AuctionCloseResult.SKIPPED);
            then(auctionEventPublisher).shouldHaveNoInteractions();
        }

        @Test
        void 진행중이_아닌_경매는_건너뛴다() {
            Auction auction = 입찰_있는_경매();
            auction.win(BIDDER_ID, HIGHEST_PRICE, END_AT);   // 즉시낙찰 등으로 이미 낙찰
            경매가_조회된다(auction);

            AuctionCloseResult result = auctionCloseUseCase.closeAuction(AUCTION_ID, END_AT.plusDays(1));

            assertThat(result).isEqualTo(AuctionCloseResult.SKIPPED);
            then(auctionEventPublisher).shouldHaveNoInteractions();
        }

        @Test
        void 같은_경매를_두_번_처리하면_두_번째는_건너뛰고_이벤트는_한_번만_발행된다() {
            Auction auction = 입찰_있는_경매();
            경매가_조회된다(auction);
            LocalDateTime now = END_AT.plusSeconds(1);

            AuctionCloseResult first = auctionCloseUseCase.closeAuction(AUCTION_ID, now);
            AuctionCloseResult second = auctionCloseUseCase.closeAuction(AUCTION_ID, now);

            assertThat(first).isEqualTo(AuctionCloseResult.WON);
            assertThat(second).isEqualTo(AuctionCloseResult.SKIPPED);
            then(auctionEventPublisher).should(times(1)).publishWon(auction);
        }
    }
}