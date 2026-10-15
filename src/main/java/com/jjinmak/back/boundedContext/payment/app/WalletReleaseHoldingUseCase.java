package com.jjinmak.back.boundedContext.payment.app;


import com.jjinmak.back.boundedContext.payment.domain.Holding;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.out.HoldingRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.event.WalletReleasedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.*;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;

/**
 * 정산: 구매 확정된 홀딩 1건의 대금을 ESCROW 에서 판매자 지갑(정산액)과 FEE 지갑(수수료)으로 옮긴다.
 *
 * 멱등: 이미 RELEASED 면 돈을 다시 옮기지 않고 회신 이벤트만 다시 발행한다.
 *      (정산 쪽이 회신을 못 받아 재요청하는 경우를 위해)
 * 락 순서: 판매자 USER(낙관적) → ESCROW(비관적) → FEE(비관적). 다른 유스케이스와 같은 순서.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WalletReleaseHoldingUseCase {

    private final SystemWalletFinder finder;
    private final HoldingRepository holdingRepository;
    private final LedgerRecorder ledgerRecorder;
    private final EventPublisher eventPublisher;

    public void release(Long orderId) {
        Holding holding = holdingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HOLDING_NOT_FOUND));

        if (holding.isReleased()) {
            publishReleased(holding);
            return;
        }

        holding.release();   // CONFIRMED 가 아니면 여기서 예외 (HELD / REFUNDED)

        Wallet seller = holding.getSellerWallet();
        Wallet escrow = finder.escrowForUpdate();
        String groupId = UUID.randomUUID().toString();

        ledgerRecorder.transfer(escrow, SETTLEMENT_PAY, seller, SETTLEMENT_RECEIVE,
                holding.getSettlementAmount(), groupId, ORDER, orderId);

        if (holding.hasFee()) {
            Wallet fee = finder.feeForUpdate();
            ledgerRecorder.transfer(escrow, FEE_PAY, fee, FEE_RECEIVE,
                    holding.getFeeAmount(), groupId, ORDER, orderId);
        }

        publishReleased(holding);
    }

    private void publishReleased(Holding holding) {
        eventPublisher.publish(new WalletReleasedEvent(
                holding.getOrderId(),
                holding.getSellerWallet().getHolder().getId(),
                holding.getAmount(),
                holding.getFeeAmount(),
                holding.getSettlementAmount(),
                holding.getReleasedAt()));
    }
}