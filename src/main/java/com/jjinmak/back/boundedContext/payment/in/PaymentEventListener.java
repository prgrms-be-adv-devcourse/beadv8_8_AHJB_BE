package com.jjinmak.back.boundedContext.payment.in;


import com.jjinmak.back.boundedContext.payment.app.WalletFacade;
import com.jjinmak.back.shared.member.event.MemberCreatedEvent;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderGroupDeletedEvent;
import com.jjinmak.back.shared.refund.event.RefundAcceptedEvent;
import com.jjinmak.back.shared.settlement.event.SettlementRequestedEvent;
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
    
    /**
     * 정산 요청 → 구매 확정된 홀딩의 대금을 ESCROW 에서 판매자 정산금·FEE 지갑으로 옮기고 WalletReleasedEvent 로 회신한다.
     * 요청에 실린 정산액·수수료가 홀딩과 다르면 돈을 옮기지 않는다. 이미 릴리즈된 건은 회신만 다시 보낸다 (멱등).
     */
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
