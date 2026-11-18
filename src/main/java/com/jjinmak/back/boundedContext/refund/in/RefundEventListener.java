package com.jjinmak.back.boundedContext.refund.in;

import com.jjinmak.back.boundedContext.refund.app.RefundFacade;
import com.jjinmak.back.shared.order.event.OrderEventTopics;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import com.jjinmak.back.shared.payment.event.WalletRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundEventListener {

    private final RefundFacade refundFacade;

    @KafkaListener(topics = OrderEventTopics.ORDER_REFUND_REQUESTED, groupId = "refund")
    public void handle(OrderRefundRequestedEvent event) {
        refundFacade.createRefund(event);
    }

    @EventListener
    public void handle(WalletRefundedEvent event) {
        refundFacade.completeRefund(event);
    }
}