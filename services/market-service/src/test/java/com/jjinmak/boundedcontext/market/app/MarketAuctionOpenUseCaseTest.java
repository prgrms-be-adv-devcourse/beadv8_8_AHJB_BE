package com.jjinmak.boundedcontext.market.app;

import com.jjinmak.boundedcontext.market.shared.domain.AuctionType;
import com.jjinmak.boundedcontext.market.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionCreateDto;
import com.jjinmak.boundedcontext.market.auction.dto.AuctionCreateResultDto;
import com.jjinmak.boundedcontext.market.product.app.ProductFacade;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductAuctionOpenDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MarketAuctionOpenUseCaseTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long AUCTION_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 21, 0);

    @Mock ProductFacade productFacade;
    @Mock AuctionFacade auctionFacade;

    @InjectMocks MarketAuctionOpenUseCase useCase;

    private AuctionCreateDto 경매_생성_요청() {
        ArgumentCaptor<AuctionCreateDto> captor = ArgumentCaptor.forClass(AuctionCreateDto.class);
        then(auctionFacade).should().createAuction(captor.capture());
        return captor.getValue();
    }

    @Test
    void 영국식은_상품이_확정한_시작_종료_시각으로_경매를_열고_상품에_경매_id를_기록한다() {
        LocalDateTime endAt = NOW.plusDays(3);
        given(productFacade.prepareAuctionOpen(PRODUCT_ID, NOW)).willReturn(Optional.of(
                new ProductAuctionOpenDto(PRODUCT_ID, 1, 100L, AuctionType.ENGLISH, 10_000L, 3_000L, "CONSOLE",
                        NOW, 80_000L, endAt, null, null, null)));
        given(auctionFacade.createAuction(any())).willReturn(new AuctionCreateResultDto(AUCTION_ID));

        useCase.openAuction(PRODUCT_ID, NOW);

        AuctionCreateDto dto = 경매_생성_요청();
        assertThat(dto.auctionType()).isEqualTo(AuctionType.ENGLISH);
        assertThat(dto.round()).isEqualTo(1);
        assertThat(dto.startAt()).isEqualTo(NOW);
        assertThat(dto.endAt()).isEqualTo(endAt);
        assertThat(dto.instantWinPrice()).isEqualTo(80_000L);
        then(productFacade).should().startAuction(PRODUCT_ID, AUCTION_ID);
    }

    @Test
    void 네덜란드식은_가격_조건을_넘겨_경매를_연다() {
        given(productFacade.prepareAuctionOpen(PRODUCT_ID, NOW)).willReturn(Optional.of(
                new ProductAuctionOpenDto(PRODUCT_ID, 1, 100L, AuctionType.DUTCH, 300_000L, 3_000L, "CONSOLE",
                        NOW, null, null, 150_000L, 2, 60)));
        given(auctionFacade.createAuction(any())).willReturn(new AuctionCreateResultDto(AUCTION_ID));

        useCase.openAuction(PRODUCT_ID, NOW);

        AuctionCreateDto dto = 경매_생성_요청();
        assertThat(dto.auctionType()).isEqualTo(AuctionType.DUTCH);
        assertThat(dto.startAt()).isEqualTo(NOW);
        assertThat(dto.floorPrice()).isEqualTo(150_000L);
        assertThat(dto.dropRatePercent()).isEqualTo(2);
        assertThat(dto.dropIntervalSec()).isEqualTo(60);
        then(productFacade).should().startAuction(PRODUCT_ID, AUCTION_ID);
    }

    @Test
    void 열_수_없는_상품이면_경매를_만들지_않는다() {
        given(productFacade.prepareAuctionOpen(PRODUCT_ID, NOW)).willReturn(Optional.empty());

        useCase.openAuction(PRODUCT_ID, NOW);

        then(auctionFacade).shouldHaveNoInteractions();
        then(productFacade).should(org.mockito.Mockito.never()).startAuction(any(), any());
    }
}
