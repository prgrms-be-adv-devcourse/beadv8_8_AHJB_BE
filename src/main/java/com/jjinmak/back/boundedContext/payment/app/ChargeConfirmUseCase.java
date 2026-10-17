package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus.REQUESTED;
import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.*;

/**
 * 수동 충전 승인. (파사드 트랜잭션 안에서 실행)
 *
 *   검증(소유자·상태·금액) → 토스 승인 → 지갑 입금 + 원장 CHARGE + Charge → CHARGED
 *
 * 토스 승인 뒤 DB 반영이 실패하면 토스 결제를 취소(보상)하고 예외를 올린다. 트랜잭션은 롤백되어 Charge 는 REQUESTED 로 남고,
 * 복구 스케줄러가 토스 상태를 보고 ABORTED 로 닫는다.
 * 원장 멱등키 "PAYMENT:{chargeId}:CHARGE" 가 유니크라 같은 충전이 두 번 기록될 수 없다.
 */
@Service
@RequiredArgsConstructor
public class ChargeConfirmUseCase {

    private final ChargeRepository chargeRepository;
    private final WalletSupport walletSupport;
    private final LedgerRecorder ledgerRecorder;
    private final PgApprovalSupport pgApproval;

    public ChargeSummaryDto confirm(Long holderId, String pgOrderId, String paymentKey, long amount) {
        Charge charge = chargeRepository.findByPgOrderId(pgOrderId)
                .orElseThrow(() -> new BusinessException(CHARGE_NOT_FOUND));

        if (!charge.getHolderId().equals(holderId)) {
            throw new BusinessException(CHARGE_FORBIDDEN);
        }
        if (charge.isCharged() && paymentKey.equals(charge.getPaymentKey())) {
            return ChargeSummaryDto.from(charge);           // 이미 승인·반영된 건 (멱등)
        }
        if (charge.getStatus() != REQUESTED) {
            throw new BusinessException(INVALID_CHARGE_STATUS);
        }
        charge.validateAmount(amount);

        TossPaymentResponse approved = pgApproval.approve(charge, paymentKey);

        try {
            Wallet wallet = walletSupport.getWalletByHolderId(holderId);
            ledgerRecorder.deposit(wallet, CHARGE, charge.getAmount(), PAYMENT, charge.getId());
            charge.markCharged(approved.paymentKey(), approved.approvedAtLocal(), LocalDateTime.now(),
                    approved.method(), approved.receiptUrl());
        } catch (RuntimeException e) {
            pgApproval.cancelApproved(charge, approved.paymentKey(), "지갑 반영 실패로 자동 취소");
            throw e;
        }
        return ChargeSummaryDto.from(charge);
    }
}
