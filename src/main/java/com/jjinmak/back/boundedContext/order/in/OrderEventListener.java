package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.app.OrderFacade;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.back.shared.member.dto.MemberDto;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import com.jjinmak.back.shared.payment.event.PaymentPaidEvent;
import com.jjinmak.back.shared.refund.event.RefundAcceptedEvent;
import com.jjinmak.back.shared.refund.event.RefundRejectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderFacade orderFacade;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(AuctionWonEvent event){
        orderFacade.createCartItem(event);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(MemberCreatedEvent event){
        MemberDto member = event.member();

        orderFacade.syncMember(member);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(DeliveryCreatedEvent event){
        orderFacade.startDelivery(event);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(RefundAcceptedEvent event){
        orderFacade.acceptRefund(event.refund());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(RefundRejectedEvent event){
        orderFacade.rejectRefund(event.refund());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(DeliveryStatusUpdatedEvent event){
        orderFacade.updateDeliveryStatus(event);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(PaymentPaidEvent event){
        orderFacade.paymentPaid(event);
    }
}
