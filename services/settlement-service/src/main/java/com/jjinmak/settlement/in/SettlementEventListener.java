package com.jjinmak.settlement.in;

import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderEventTopics;
import com.jjinmak.contracts.payment.event.PaymentEventTopics;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import com.jjinmak.contracts.product.event.ProductEventTopics;
import com.jjinmak.contracts.product.event.ProductRegisteredEvent;
import com.jjinmak.settlement.app.SettlementFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementEventListener {

    private final SettlementFacade settlementFacade;

    @KafkaListener(topics = ProductEventTopics.PRODUCT_REGISTERED, groupId = "settlement")
    public void handle(ProductRegisteredEvent event) {
        settlementFacade.saveProduct(event);
    }

    @KafkaListener(topics = OrderEventTopics.ORDER_CONFIRM_PURCHASE, groupId = "settlement")
    public void handle(OrderConfirmPurchaseEvent event) {
        settlementFacade.createSettlement(event);
    }

    @KafkaListener(topics = PaymentEventTopics.PAYMENT_RELEASED_EVENT, groupId = "settlement")
    public void handle(WalletReleasedEvent event) {
        settlementFacade.completeSettlement(event);
    }
}
