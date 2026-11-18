package com.jjinmak.back.boundedContext.settlement.in;

import com.jjinmak.back.boundedContext.settlement.app.SettlementFacade;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderEventTopics;
import com.jjinmak.back.shared.payment.event.WalletReleasedEvent;
import com.jjinmak.back.shared.product.event.ProductEventTopics;
import com.jjinmak.back.shared.product.event.ProductRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
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

    @EventListener
    public void handle(WalletReleasedEvent event) {
        settlementFacade.completeSettlement(event);
    }
}