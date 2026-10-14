package com.jjinmak.back.boundedContext.wallet.app;


import com.jjinmak.back.boundedContext.wallet.domain.Holding;
import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.wallet.domain.enums.HoldingStatus;
import com.jjinmak.back.boundedContext.wallet.out.HoldingRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletTransactionRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.wallet.event.WalletRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.wallet.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType.REFUND_PAY;
import static com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType.REFUND_RECEIVE;
import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WalletRefundOrderUseCase {

    private final SystemWalletFinder finder;
    private final HoldingRepository holdingRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final EventPublisher eventPublisher;


    public void refund(Long refundId, Long orderId, long requestedAmount) {
        Holding holding = holdingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HOLDING_NOT_FOUND));

        if (holding.isRefunded()) return;

        Wallet buyer = holding.getBuyerWallet();
        Wallet escrow = finder.escrowForUpdate();
        long amount = holding.getAmount();

        holding.refund(requestedAmount);
        escrow.withdrawal(amount);
        buyer.deposit(amount);

        String groupId = UUID.randomUUID().toString();

        walletTransactionRepository.save(
                WalletTransaction.of(escrow, REFUND_PAY, -amount, groupId, ORDER, orderId)
        );
        walletTransactionRepository.save(
                WalletTransaction.of(buyer, REFUND_RECEIVE, amount, groupId, ORDER, orderId)
        );

        eventPublisher.publish(new WalletRefundedEvent(
                refundId, orderId, holding.getPaymentId(), amount, holding.getRefundedAt()));
    }
}
