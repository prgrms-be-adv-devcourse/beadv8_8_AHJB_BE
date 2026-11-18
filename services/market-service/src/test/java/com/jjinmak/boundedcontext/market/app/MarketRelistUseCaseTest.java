package com.jjinmak.boundedcontext.market.app;

import com.jjinmak.boundedcontext.auction.app.AuctionFacade;
import com.jjinmak.boundedcontext.auction.dto.AuctionCreateDto;
import com.jjinmak.boundedcontext.auction.dto.AuctionCreateResultDto;
import com.jjinmak.boundedcontext.auction.exception.AuctionErrorCode;
import com.jjinmak.boundedcontext.market.in.dto.MarketRelistResponseDto;
import com.jjinmak.boundedcontext.product.product.app.ProductFacade;
import com.jjinmak.boundedcontext.product.product.domain.AuctionType;
import com.jjinmak.boundedcontext.product.product.in.dto.ProductAuctionOpenDto;
import com.jjinmak.boundedcontext.product.product.in.dto.ProductRelistDto;
import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MarketRelistUseCaseTest {

    private static final Long SELLER_ID = 100L;
    private static final Long PRODUCT_ID = 1L;
    private static final Long PREVIOUS_AUCTION_ID = 10L;
    private static final Long NEW_AUCTION_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 21, 0);
    private static final LocalDateTime END_AT = NOW.plusDays(3);

    @Mock ProductFacade productFacade;
    @Mock AuctionFacade auctionFacade;

    @InjectMocks MarketRelistUseCase useCase;

    private void 상품이_2회차로_넘어간다() {
        given(productFacade.relist(SELLER_ID, PRODUCT_ID, NOW)).willReturn(new ProductRelistDto(PREVIOUS_AUCTION_ID,
                new ProductAuctionOpenDto(PRODUCT_ID, 2, SELLER_ID, AuctionType.ENGLISH, 10_000L, 3_000L, "CONSOLE",
                        NOW, null, END_AT, null, null, null)));
    }

    @Test
    void 이전_회차를_확인하고_새_회차_경매를_바로_열어_경매_id와_회차를_돌려준다() {
        상품이_2회차로_넘어간다();
        given(auctionFacade.createAuction(any())).willReturn(new AuctionCreateResultDto(NEW_AUCTION_ID, END_AT));

        MarketRelistResponseDto response = useCase.relist(SELLER_ID, PRODUCT_ID, NOW);

        then(auctionFacade).should().validateRelistable(PREVIOUS_AUCTION_ID);
        ArgumentCaptor<AuctionCreateDto> captor = ArgumentCaptor.forClass(AuctionCreateDto.class);
        then(auctionFacade).should().createAuction(captor.capture());
        assertThat(captor.getValue().round()).isEqualTo(2);
        assertThat(captor.getValue().startAt()).isEqualTo(NOW);
        assertThat(captor.getValue().endAt()).isEqualTo(END_AT);
        then(productFacade).should().startAuction(PRODUCT_ID, NEW_AUCTION_ID, END_AT);
        assertThat(response).isEqualTo(new MarketRelistResponseDto(NEW_AUCTION_ID, 2));
    }

    @Test
    void 이전_회차를_다시_열_수_없으면_새_경매를_만들지_않는다() {
        상품이_2회차로_넘어간다();
        willThrow(new BusinessException(AuctionErrorCode.AUCTION_NOT_RELISTABLE))
                .given(auctionFacade).validateRelistable(PREVIOUS_AUCTION_ID);

        assertThatThrownBy(() -> useCase.relist(SELLER_ID, PRODUCT_ID, NOW))
                .extracting("errorCode").isEqualTo(AuctionErrorCode.AUCTION_NOT_RELISTABLE);

        then(auctionFacade).should(never()).createAuction(any());
        then(productFacade).should(never()).startAuction(any(), any(), any());
    }
}
