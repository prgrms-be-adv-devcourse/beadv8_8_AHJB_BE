package com.jjinmak.payment.in;


import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.contracts.order.event.OrderGroupDeletedEvent;
import com.jjinmak.contracts.payment.event.WalletMemberCreatedEvent;
import com.jjinmak.contracts.refund.event.RefundAcceptedEvent;
import com.jjinmak.contracts.refund.event.RefundRetryRequestedEvent;
import com.jjinmak.contracts.settlement.event.SettlementRequestedEvent;
import com.jjinmak.payment.app.facade.WalletFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final WalletFacade walletFacade;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(MemberCreatedEvent event) {
        walletFacade.syncMember(event.member());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(WalletMemberCreatedEvent event) {
        walletFacade.createWallet(event.getMember());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(RefundAcceptedEvent event) {
        walletFacade.refund(event.refund().refundId(), event.refund().orderId(), event.refund().amount());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(RefundRetryRequestedEvent event) {
        walletFacade.refund(event.refund().refundId(), event.refund().orderId(), event.refund().amount());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(OrderConfirmPurchaseEvent event) {
        walletFacade.confirmHolding(event.orderId());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(SettlementRequestedEvent event) {
        walletFacade.releaseHolding(
                event.settlement().orderId(),
                event.settlement().settlementAmount(),
                event.settlement().feeAmount());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(OrderGroupDeletedEvent event) {
        walletFacade.closePaymentByOrderGroupDeleted(event.orderGroupId());
    }
}
