package com.jjinmak.auction.app;

import com.jjinmak.auction.domain.Auction;
import com.jjinmak.auction.domain.DutchAuction;
import com.jjinmak.auction.domain.EnglishAuction;
import com.jjinmak.auction.dto.AuctionCreateDto;
import com.jjinmak.auction.out.AuctionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
        return AuctionCreateDto.english(PRODUCT_ID, 100L, 10_000L, 3_000L,
                now, "CONSOLE", null, now.plusDays(3));
    }

    @Test
    void 처음_받은_요청이면_경매를_생성하고_시작_이벤트를_발행한다() {
        given(auctionRepository.existsByProductId(PRODUCT_ID)).willReturn(false);
        given(auctionRepository.save(any(Auction.class))).willAnswer(inv -> inv.getArgument(0));

        auctionCreateUseCase.createAuction(생성_요청());

        then(auctionRepository).should().save(any(Auction.class));
        then(auctionEventPublisher).should().publishStarted(any(EnglishAuction.class));
    }

    @Test
    void 같은_상품의_경매가_이미_있으면_상태와_무관하게_생성하지_않는다() {
        given(auctionRepository.existsByProductId(PRODUCT_ID)).willReturn(true);

        auctionCreateUseCase.createAuction(생성_요청());

        then(auctionRepository).should(never()).save(any());
        then(auctionEventPublisher).should(never()).publishStarted(any());
    }

    @Test
    void 네덜란드식_요청이면_가격_곡선으로_종료_시각을_계산해_생성한다() {
        LocalDateTime startAt = LocalDateTime.now().plusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        AuctionCreateDto dto = AuctionCreateDto.dutch(PRODUCT_ID, 100L, 300_000L, 3_000L,
                startAt, "CONSOLE", 150_000L, 2, 60);
        given(auctionRepository.existsByProductId(PRODUCT_ID)).willReturn(false);
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
