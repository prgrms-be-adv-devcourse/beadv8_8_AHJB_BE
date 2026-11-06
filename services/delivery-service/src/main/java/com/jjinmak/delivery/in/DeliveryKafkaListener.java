package com.jjinmak.delivery.in;

import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.order.event.OrderEventTopics;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import com.jjinmak.delivery.app.DeliveryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryKafkaListener {

    private final DeliveryFacade deliveryFacade;
    private final String groupId = "delivery";

    @KafkaListener(topics = MemberEventTopics.MEMBER_CREATED, groupId = groupId)
    public void handle(MemberCreatedEvent event){
        deliveryFacade.syncMember(event.member());
    }

    @KafkaListener(topics = OrderEventTopics.ORDER_SEND_PRODUCT, groupId = groupId)
    public void handle(OrderSendProductEvent event){
        deliveryFacade.createDelivery(event);
    }
}
