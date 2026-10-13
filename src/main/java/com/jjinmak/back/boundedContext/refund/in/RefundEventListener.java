package com.jjinmak.back.boundedContext.refund.in;

import com.jjinmak.back.boundedContext.refund.app.RefundFacade;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
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
}
