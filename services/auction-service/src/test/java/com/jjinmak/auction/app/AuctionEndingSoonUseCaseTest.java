package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.AuctionStatus;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.out.AuctionRepository;
import com.jjinmak.auction.out.BidRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuctionEndingSoonUseCaseTest {

    private static final Long AUCTION_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 20, 50);

    @Mock AuctionRepository auctionRepository;
    @Mock BidRepository bidRepository;
    @Mock AuctionReader auctionReader;
    @Mock AuctionEventPublisher auctionEventPublisher;

    @InjectMocks AuctionEndingSoonUseCase auctionEndingSoonUseCase;

    private EnglishAuction 입찰_있는_경매() {
        EnglishAuction auction = EnglishAuction.create(10L, 100L, 10_000L, null, 3_000L, "CONSOLE",
                NOW.plusMinutes(5), NOW.minusDays(3));
        auction.updateHighestBid(200L, 10_000L);
        return auction;
    }

    @Test
    void 대상은_진행중_경매_중_지금부터_10분_안에_끝나는_것으로_찾는다() {
        auctionEndingSoonUseCase.findTargetIds(NOW, 1000);

        then(auctionRepository).should().findEndingSoonTargetIds(
                AuctionStatus.IN_PROGRESS, NOW, NOW.plusMinutes(10), PageRequest.of(0, 1000));
    }

    @Test
    void 처음_표시에_성공하면_입찰자_전원과_함께_마감_임박_이벤트를_발행한다() {
        EnglishAuction auction = 입찰_있는_경매();
        given(auctionRepository.markEndingSoonNotified(AUCTION_ID, AuctionStatus.IN_PROGRESS, NOW)).willReturn(1);
        given(auctionReader.getEnglish(AUCTION_ID)).willReturn(auction);
        given(bidRepository.findDistinctBidderIds(AUCTION_ID)).willReturn(List.of(200L, 300L));

        boolean published = auctionEndingSoonUseCase.notifyEndingSoon(AUCTION_ID, NOW);

        assertThat(published).isTrue();
        then(auctionEventPublisher).should().publishEndingSoon(auction, List.of(200L, 300L), NOW);
    }

    @Test
    void 이미_표시됐거나_그사이_종료된_경매는_발행하지_않고_조회도_하지_않는다() {
        given(auctionRepository.markEndingSoonNotified(AUCTION_ID, AuctionStatus.IN_PROGRESS, NOW)).willReturn(0);

        boolean published = auctionEndingSoonUseCase.notifyEndingSoon(AUCTION_ID, NOW);

        assertThat(published).isFalse();
        then(auctionReader).should(never()).getEnglish(any());
        then(auctionEventPublisher).should(never()).publishEndingSoon(any(), any(), any());
    }
}
