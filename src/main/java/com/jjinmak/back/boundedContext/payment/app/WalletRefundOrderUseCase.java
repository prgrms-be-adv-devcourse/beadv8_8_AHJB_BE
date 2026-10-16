package com.jjinmak.back.boundedContext.payment.app;


import com.jjinmak.back.boundedContext.payment.domain.Holding;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.out.HoldingRepository;
import com.jjinmak.back.boundedContext.payment.out.PaymentRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.event.WalletRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.REFUND_PAY;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.REFUND_RECEIVE;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WalletRefundOrderUseCase {

    private final SystemWalletFinder finder;
    private final HoldingRepository holdingRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerRecorder ledgerRecorder;
    private final EventPublisher eventPublisher;


    public void refund(Long refundId, Long orderId, long requestedAmount) {
        Holding holding = holdingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HOLDING_NOT_FOUND));

        if (holding.isRefunded()) return;

        Wallet buyer = holding.getBuyerWallet();
        Wallet escrow = finder.escrowForUpdate();
        long amount = holding.getAmount();

        holding.refund(requestedAmount);

        // 결제 기록에도 환불 시각을 남긴다. (Holding.paymentId 는 Payment 의 id)
        paymentRepository.findWithItemsById(holding.getPaymentId())
                .ifPresent(payment -> payment.markItemRefunded(orderId, holding.getRefundedAt()));
        ledgerRecorder.transfer(escrow, REFUND_PAY, buyer, REFUND_RECEIVE,
                amount, UUID.randomUUID().toString(), ORDER, orderId);

        eventPublisher.publish(new WalletRefundedEvent(
                refundId, orderId, holding.getPaymentId(), amount, holding.getRefundedAt()));
    }
}
