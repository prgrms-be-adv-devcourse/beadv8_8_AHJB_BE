package com.jjinmak.back.boundedContext.settlement.app;

import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementFacade {

    private final SettlementCreateSettlementUseCase settlementCreateSettlementUseCase;

    @Transactional
    public void createSettlement(OrderConfirmPurchaseEvent event) {
        settlementCreateSettlementUseCase.createSettlement(event);
    }
}
