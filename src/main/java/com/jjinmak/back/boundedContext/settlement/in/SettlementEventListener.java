package com.jjinmak.back.boundedContext.settlement.in;

import com.jjinmak.back.boundedContext.settlement.app.SettlementFacade;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.payment.event.WalletReleasedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementEventListener {

    private final SettlementFacade settlementFacade;

    @EventListener
    public void handle(OrderConfirmPurchaseEvent event) {
        settlementFacade.createSettlement(event);
    }

    @EventListener
    public void handle(WalletReleasedEvent event) {
        settlementFacade.completeSettlement(event);
    }
}
