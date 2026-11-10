package com.jjinmak.settlement.app;

import com.jjinmak.settlement.domain.SettlementProduct;
import com.jjinmak.settlement.out.SettlementProductRepository;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SettlementSaveProductUseCaseTest {

    private static final Long PRODUCT_ID = 10L;
    private static final ProductRegisteredEvent EVENT = new ProductRegisteredEvent(
            PRODUCT_ID, 100L, 3_000L, 10_000L, null, 3, null, "슈퍼패미컴", "CONSOLE", null);

    @Mock SettlementProductRepository settlementProductRepository;

    @InjectMocks SettlementSaveProductUseCase settlementSaveProductUseCase;

    @Test
    void 처음_받은_상품은_복사본을_저장한다() {
        given(settlementProductRepository.existsById(PRODUCT_ID)).willReturn(false);

        settlementSaveProductUseCase.saveProduct(EVENT);

        ArgumentCaptor<SettlementProduct> captor = ArgumentCaptor.forClass(SettlementProduct.class);
        then(settlementProductRepository).should().save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(PRODUCT_ID);
        assertThat(captor.getValue().getName()).isEqualTo("슈퍼패미컴");
    }

    @Test
    void 같은_상품을_두_번_받으면_두_번째는_저장하지_않는다() {
        given(settlementProductRepository.existsById(PRODUCT_ID)).willReturn(true);

        settlementSaveProductUseCase.saveProduct(EVENT);

        then(settlementProductRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }
}