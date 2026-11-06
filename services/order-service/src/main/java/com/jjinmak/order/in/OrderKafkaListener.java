package com.jjinmak.order.in;

import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryEventTopics;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.payment.event.PaymentEventTopics;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.contracts.refund.event.RefundAcceptedEvent;
import com.jjinmak.contracts.refund.event.RefundEventTopics;
import com.jjinmak.contracts.refund.event.RefundRejectedEvent;
import com.jjinmak.order.app.OrderFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderKafkaListener {

    private final OrderFacade orderFacade;
    private final String groupId = "order";

    @KafkaListener(topics = AuctionEventTopics.AUCTION_WON, groupId = groupId)
    public void handle(AuctionWonEvent event){
        orderFacade.createCartItem(event);
    }

    @KafkaListener(topics = MemberEventTopics.MEMBER_CREATED, groupId = groupId)
    public void handle(MemberCreatedEvent event){
        MemberDto member = event.member();

        orderFacade.syncMember(member);
    }

    @KafkaListener(topics = DeliveryEventTopics.DELIVERY_CREATED, groupId = groupId)
    public void handle(DeliveryCreatedEvent event){
        orderFacade.startDelivery(event);
    }

    @KafkaListener(topics = RefundEventTopics.REFUND_ACCEPTED, groupId = groupId)
    public void handle(RefundAcceptedEvent event){
        orderFacade.acceptRefund(event.refund());
    }

    @KafkaListener(topics = RefundEventTopics.REFUND_REJECTED, groupId = groupId)
    public void handle(RefundRejectedEvent event){
        orderFacade.rejectRefund(event.refund());
    }

    @KafkaListener(topics = DeliveryEventTopics.DELIVERY_STATUS_UPDATED, groupId = groupId)
    public void handle(DeliveryStatusUpdatedEvent event){
        orderFacade.updateDeliveryStatus(event);
    }

    @KafkaListener(topics = PaymentEventTopics.PAYMENT_PAID_EVENT, groupId = groupId)
    public void handle(PaymentPaidEvent event){
        orderFacade.paymentPaid(event);
    }
}
