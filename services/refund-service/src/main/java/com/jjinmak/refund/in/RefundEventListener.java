package com.jjinmak.refund.in;

import com.jjinmak.contracts.order.event.OrderEventTopics;
import com.jjinmak.contracts.order.event.OrderRefundRequestedEvent;
import com.jjinmak.contracts.payment.event.PaymentEventTopics;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import com.jjinmak.refund.app.RefundFacade;
import lombok.RequiredArgsConstructor;
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

    @KafkaListener(topics = PaymentEventTopics.PAYMENT_REFUND_EVENT, groupId = "refund")
    public void handle(WalletRefundedEvent event) {
        refundFacade.completeRefund(event);
    }
}
