package com.jjinmak.settlement.app;

import com.jjinmak.settlement.domain.Settlement;
import com.jjinmak.settlement.exception.SettlementErrorCode;
import com.jjinmak.settlement.domain.SettlementProduct;
import com.jjinmak.settlement.out.SettlementProductRepository;
import com.jjinmak.settlement.out.SettlementRepository;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SettlementCreateSettlementUseCaseTest {

    private static final Long ORDER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final OrderConfirmPurchaseEvent EVENT = new OrderConfirmPurchaseEvent(
            ORDER_ID, 200L, 100L, 5L, PRODUCT_ID, 50_000L, 3_000L,
            LocalDateTime.of(2026, 11, 3, 12, 0));

    @Mock SettlementRepository settlementRepository;
    @Mock SettlementProductRepository settlementProductRepository;

    @InjectMocks SettlementCreateSettlementUseCase settlementCreateSettlementUseCase;

    @Test
    void 복사본의_상품명으로_정산서를_만든다() {
        given(settlementRepository.existsByOrderId(ORDER_ID)).willReturn(false);
        given(settlementProductRepository.findById(PRODUCT_ID))
                .willReturn(Optional.of(new SettlementProduct(PRODUCT_ID, "슈퍼패미컴")));

        settlementCreateSettlementUseCase.createSettlement(EVENT);

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        then(settlementRepository).should().save(captor.capture());
        assertThat(captor.getValue().getProductName()).isEqualTo("슈퍼패미컴");
        assertThat(captor.getValue().getSettlementAmount()).isEqualTo(48_000L);
    }

    @Test
    void 복사본이_없으면_예외를_던진다() {
        given(settlementRepository.existsByOrderId(ORDER_ID)).willReturn(false);
        given(settlementProductRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementCreateSettlementUseCase.createSettlement(EVENT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SettlementErrorCode.PRODUCT_NOT_FOUND);
        then(settlementRepository).should(never()).save(any());
    }

    @Test
    void 같은_주문을_두_번_받으면_두_번째는_만들지_않는다() {
        given(settlementRepository.existsByOrderId(ORDER_ID)).willReturn(true);

        settlementCreateSettlementUseCase.createSettlement(EVENT);

        then(settlementProductRepository).shouldHaveNoInteractions();
        then(settlementRepository).should(never()).save(any());
    }
}