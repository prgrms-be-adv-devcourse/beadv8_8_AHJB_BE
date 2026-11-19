package com.jjinmak.boundedcontext.market.auction.app;

import com.jjinmak.boundedcontext.market.auction.domain.Bid;
import com.jjinmak.boundedcontext.market.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.market.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionBidHistoryItemDto;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionBidHistoryResponseDto;
import com.jjinmak.boundedcontext.market.auction.exception.AuctionErrorCode;
import com.jjinmak.boundedcontext.market.auction.out.BidRepository;
import com.jjinmak.boundedcontext.market.shared.domain.MarketMember;
import com.jjinmak.boundedcontext.market.shared.out.MarketMemberRepository;
import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuctionBidHistoryUseCaseTest {

    private static final Long AUCTION_ID = 1L;
    private static final Long ME = 200L;
    private static final Long OTHER = 300L;
    private static final LocalDateTime BID_AT = LocalDateTime.of(2026, 11, 19, 20, 0);

    @Mock AuctionReader auctionReader;
    @Mock BidRepository bidRepository;
    @Mock MarketMemberRepository marketMemberRepository;
    @InjectMocks AuctionBidHistoryUseCase useCase;

    private EnglishAuction 영국식(int bidCount) {
        EnglishAuction auction = mock(EnglishAuction.class);
        given(auction.getBidCount()).willReturn(bidCount);
        return auction;
    }

    @Test
    void 전체_개수는_count_쿼리가_아니라_경매의_입찰_수를_쓴다() {
        EnglishAuction auction = 영국식(37);
        given(auctionReader.get(AUCTION_ID)).willReturn(auction);
        given(bidRepository.findByAuctionIdOrderByIdDesc(AUCTION_ID, PageRequest.of(0, 2)))
                .willReturn(new SliceImpl<>(List.of(new Bid(AUCTION_ID, OTHER, 16_000L, BID_AT),
                        new Bid(AUCTION_ID, ME, 15_000L, BID_AT)), PageRequest.of(0, 2), true));
        given(marketMemberRepository.findAllById(any()))
                .willReturn(List.of(new MarketMember(ME, "경매왕"), new MarketMember(OTHER, "게임러")));

        AuctionBidHistoryResponseDto response = useCase.bidHistory(AUCTION_ID, ME, 0, 2);

        assertThat(response.totalElements()).isEqualTo(37);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).extracting(AuctionBidHistoryItemDto::bidderNickname)
                .containsExactly("게**", "경**");
        assertThat(response.content()).extracting(AuctionBidHistoryItemDto::isMine)
                .containsExactly(false, true);
    }

    @Test
    void 페이지와_크기를_보정해서_조회한다() {
        EnglishAuction auction = 영국식(0);
        given(auctionReader.get(AUCTION_ID)).willReturn(auction);
        given(bidRepository.findByAuctionIdOrderByIdDesc(AUCTION_ID, PageRequest.of(0, 50)))
                .willReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 50), false));
        given(marketMemberRepository.findAllById(any())).willReturn(List.of());

        AuctionBidHistoryResponseDto response = useCase.bidHistory(AUCTION_ID, null, -1, 100);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(50);
        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 닉네임이_없는_입찰자는_기본값으로_표시한다() {
        EnglishAuction auction = 영국식(1);
        given(auctionReader.get(AUCTION_ID)).willReturn(auction);
        given(bidRepository.findByAuctionIdOrderByIdDesc(AUCTION_ID, PageRequest.of(0, 10)))
                .willReturn(new SliceImpl<>(List.of(new Bid(AUCTION_ID, OTHER, 10_000L, BID_AT)), PageRequest.of(0, 10), false));
        given(marketMemberRepository.findAllById(any())).willReturn(List.of());

        AuctionBidHistoryResponseDto response = useCase.bidHistory(AUCTION_ID, null, 0, 10);

        assertThat(response.content()).extracting(AuctionBidHistoryItemDto::bidderNickname).containsExactly("알 수 없음");
    }

    @Test
    void 하향식_경매는_입찰_행이_없어_조회하지_않고_빈_목록을_돌려준다() {
        given(auctionReader.get(AUCTION_ID)).willReturn(mock(DutchAuction.class));

        AuctionBidHistoryResponseDto response = useCase.bidHistory(AUCTION_ID, ME, 0, 10);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        then(bidRepository).should(never()).findByAuctionIdOrderByIdDesc(anyLong(), any());
    }

    @Test
    void 경매가_없으면_AUCTION001() {
        given(auctionReader.get(AUCTION_ID)).willThrow(new BusinessException(AuctionErrorCode.AUCTION_NOT_FOUND));

        assertThatThrownBy(() -> useCase.bidHistory(AUCTION_ID, ME, 0, 10))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuctionErrorCode.AUCTION_NOT_FOUND);
        then(bidRepository).should(never()).findByAuctionIdOrderByIdDesc(anyLong(), any());
    }
}
