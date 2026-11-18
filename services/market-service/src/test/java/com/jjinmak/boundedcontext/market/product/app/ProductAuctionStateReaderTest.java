package com.jjinmak.boundedcontext.market.product.app;

import com.jjinmak.boundedcontext.auction.app.AuctionQueryFacade;
import com.jjinmak.boundedcontext.auction.domain.AuctionStatus;
import com.jjinmak.boundedcontext.auction.domain.FailureReason;
import com.jjinmak.boundedcontext.auction.dto.AuctionStateDto;
import com.jjinmak.boundedcontext.market.product.domain.Product;
import com.jjinmak.boundedcontext.market.product.domain.ProductAuctionFailureReason;
import com.jjinmak.boundedcontext.market.product.domain.ProductAuctionStatus;
import com.jjinmak.boundedcontext.market.product.in.dto.ProductAuctionStateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ProductAuctionStateReaderTest {

    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 11, 21, 21, 0);

    @Mock AuctionQueryFacade auctionQueryFacade;

    @InjectMocks ProductAuctionStateReader reader;

    private Product 상품(Long productId, Long auctionId) {
        Product product = mock(Product.class);
        given(product.getId()).willReturn(productId);
        given(product.getCurrentAuctionId()).willReturn(auctionId);
        return product;
    }

    @Test
    void 경매가_열리지_않은_상품은_시작_전이다() {
        given(auctionQueryFacade.getAuctionStates(Set.of())).willReturn(Map.of());

        Map<Long, ProductAuctionStateDto> states = reader.read(List.of(상품(1L, null)));

        assertThat(states.get(1L)).isEqualTo(ProductAuctionStateDto.scheduled());
    }

    // 결제완료·구매확정은 상품에서 낙찰(SOLD)로 묶는다 (기존 복사본과 같은 규칙)
    @ParameterizedTest
    @CsvSource({
            "IN_PROGRESS, IN_PROGRESS",
            "WON, SOLD",
            "PAID, SOLD",
            "COMPLETED, SOLD"
    })
    void 경매_상태를_상품_상태로_바꾼다(AuctionStatus auctionStatus, ProductAuctionStatus expected) {
        given(auctionQueryFacade.getAuctionStates(any()))
                .willReturn(Map.of(10L, new AuctionStateDto(10L, auctionStatus, null, END_AT)));

        ProductAuctionStateDto state = reader.read(List.of(상품(1L, 10L))).get(1L);

        assertThat(state).isEqualTo(new ProductAuctionStateDto(expected, END_AT, null));
    }

    @Test
    void 무산된_경매는_사유를_함께_돌려준다() {
        given(auctionQueryFacade.getAuctionStates(any()))
                .willReturn(Map.of(10L, new AuctionStateDto(10L, AuctionStatus.FAILED, FailureReason.UNPAID, END_AT)));

        ProductAuctionStateDto state = reader.read(List.of(상품(1L, 10L))).get(1L);

        assertThat(state).isEqualTo(
                new ProductAuctionStateDto(ProductAuctionStatus.FAILED, END_AT, ProductAuctionFailureReason.UNPAID));
    }

    @Test
    void 여러_상품의_경매를_한_번에_조회한다() {
        given(auctionQueryFacade.getAuctionStates(Set.of(10L, 20L))).willReturn(Map.of(
                10L, new AuctionStateDto(10L, AuctionStatus.IN_PROGRESS, null, END_AT),
                20L, new AuctionStateDto(20L, AuctionStatus.FAILED, FailureReason.NO_BID, END_AT)));

        Map<Long, ProductAuctionStateDto> states = reader.read(List.of(상품(1L, 10L), 상품(2L, 20L), 상품(3L, null)));

        assertThat(states.get(1L).status()).isEqualTo(ProductAuctionStatus.IN_PROGRESS);
        assertThat(states.get(2L).failureReason()).isEqualTo(ProductAuctionFailureReason.NO_BID);
        assertThat(states.get(3L).status()).isEqualTo(ProductAuctionStatus.SCHEDULED);
    }
}
