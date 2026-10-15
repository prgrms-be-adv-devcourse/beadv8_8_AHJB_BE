package com.jjinmak.back.shared.refund.event;

import com.jjinmak.back.boundedContext.refund.app.RefundFacade;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import com.jjinmak.back.shared.wallet.event.WalletRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundEventListener {

    private final RefundFacade refundFacade;

    @EventListener
    public void handle(OrderRefundRequestedEvent event) {
        refundFacade.createRefund(event);
    }

    @EventListener
    public void handle(WalletRefundedEvent event) {
        refundFacade.completeRefund(event);
    }
}
