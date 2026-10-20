package com.jjinmak.back.boundedContext.payment.in;


import com.jjinmak.back.boundedContext.payment.app.WalletFacade;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderGroupDeletedEvent;
import com.jjinmak.back.shared.refund.event.RefundAcceptedEvent;
import com.jjinmak.back.shared.payment.event.WalletMemberCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
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
    public void handle(OrderConfirmPurchaseEvent event) {
        walletFacade.confirmHolding(event.orderId());
    }

    /** 주문 스케줄러가 결제되지 않은 주문 그룹을 삭제하면 → 그 그룹의 미완료 결제를 FAILED 로 닫는다 */
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(OrderGroupDeletedEvent event) {
        walletFacade.closePaymentByOrderGroupDeleted(event.orderGroupId());
    }
}
