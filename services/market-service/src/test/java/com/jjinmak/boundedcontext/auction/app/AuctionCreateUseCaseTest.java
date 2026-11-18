package com.jjinmak.boundedcontext.auction.app;

import com.jjinmak.boundedcontext.auction.domain.Auction;
import com.jjinmak.boundedcontext.auction.domain.DutchAuction;
import com.jjinmak.boundedcontext.auction.domain.EnglishAuction;
import com.jjinmak.boundedcontext.auction.dto.AuctionCreateDto;
import com.jjinmak.boundedcontext.auction.dto.AuctionCreateResultDto;
import com.jjinmak.boundedcontext.auction.out.AuctionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuctionCreateUseCaseTest {

    private static final Long PRODUCT_ID = 1L;

    @Mock AuctionRepository auctionRepository;
    @Mock AuctionEventPublisher auctionEventPublisher;

    @InjectMocks AuctionCreateUseCase auctionCreateUseCase;

    private AuctionCreateDto 생성_요청() {
        LocalDateTime now = LocalDateTime.now();
        return AuctionCreateDto.english(PRODUCT_ID, 1, 100L, 10_000L, 3_000L,
                now, "CONSOLE", null, now.plusDays(3));
    }

    @Test
    void 처음_받은_요청이면_경매를_생성하고_시작_이벤트를_발행한다() {
        given(auctionRepository.findByProductIdAndRound(PRODUCT_ID, 1)).willReturn(Optional.empty());
        given(auctionRepository.save(any(Auction.class))).willAnswer(inv -> inv.getArgument(0));
        AuctionCreateDto request = 생성_요청();

        auctionCreateUseCase.createAuction(request);

        then(auctionRepository).should().save(any(Auction.class));
        then(auctionEventPublisher).should().publishStarted(any(EnglishAuction.class));
    }

    @Test
    void 같은_상품의_경매가_이미_있으면_생성하지_않고_기존_경매를_돌려준다() {
        AuctionCreateDto request = 생성_요청();
        EnglishAuction existing = EnglishAuction.create(PRODUCT_ID, 100L, 10_000L, null, 3_000L,
                "CONSOLE", request.endAt(), request.startAt());
        given(auctionRepository.findByProductIdAndRound(PRODUCT_ID, 1)).willReturn(Optional.of(existing));

        AuctionCreateResultDto result = auctionCreateUseCase.createAuction(request);

        then(auctionRepository).should(never()).save(any());
        then(auctionEventPublisher).should(never()).publishStarted(any());
        assertThat(result.auctionId()).isEqualTo(existing.getId());
    }

    @Test
    void 같은_상품이라도_다음_회차면_새_경매를_생성한다() {
        LocalDateTime now = LocalDateTime.now();
        AuctionCreateDto secondRound = AuctionCreateDto.english(PRODUCT_ID, 2, 100L, 10_000L, 3_000L,
                now, "CONSOLE", null, now.plusDays(3));
        given(auctionRepository.findByProductIdAndRound(PRODUCT_ID, 2)).willReturn(Optional.empty());
        given(auctionRepository.save(any(Auction.class))).willAnswer(inv -> inv.getArgument(0));

        auctionCreateUseCase.createAuction(secondRound);

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        then(auctionRepository).should().save(captor.capture());
        assertThat(captor.getValue().getRound()).isEqualTo(2);
    }

    @Test
    void 네덜란드식_요청이면_가격_곡선으로_종료_시각을_계산해_생성한다() {
        LocalDateTime startAt = LocalDateTime.now().plusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        AuctionCreateDto dto = AuctionCreateDto.dutch(PRODUCT_ID, 1, 100L, 300_000L, 3_000L,
                startAt, "CONSOLE", 150_000L, 2, 60);
        given(auctionRepository.findByProductIdAndRound(PRODUCT_ID, 1)).willReturn(Optional.empty());
        given(auctionRepository.save(any(Auction.class))).willAnswer(inv -> inv.getArgument(0));

        auctionCreateUseCase.createAuction(dto);

        ArgumentCaptor<Auction> captor = ArgumentCaptor.forClass(Auction.class);
        then(auctionRepository).should().save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(DutchAuction.class);
        assertThat(captor.getValue().getStartAt()).isEqualTo(startAt);
        assertThat(captor.getValue().getEndAt()).isEqualTo(startAt.plusMinutes(26));
        then(auctionEventPublisher).should().publishStarted(captor.getValue());
    }
}
