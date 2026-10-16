package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletTransactionRepository;
import com.jjinmak.back.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus.CANCELED;
import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.CHARGE_CANCEL;
import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.*;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.INVALID_AMOUNT;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.TRANSACTION_NOT_FOUND;

/**
 * 충전 취소 (전액). (파사드 트랜잭션 안에서 실행)
 *
 *   검증(소유자·취소 가능 상태) → 지갑 차감 + 원장 CHARGE_CANCEL → 토스 취소 → Charge → CANCELED
 *
 * 지갑 차감을 토스 취소보다 먼저 하는 이유: 잔액이 부족하면(이미 예치금을 썼으면) 토스를 부르기 전에 INSUFFICIENT_BALANCE 로 끝난다.
 * 토스 취소가 실패하면 예외로 롤백되어 차감도 함께 되돌아가므로 별도 보상(REVERSAL)이 필요 없다.
 */
@Service
@RequiredArgsConstructor
public class ChargeCancelUseCase {

    private final ChargeRepository chargeRepository;
    private final WalletSupport walletSupport;
    private final LedgerRecorder ledgerRecorder;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PgApprovalSupport pgApproval;

    public ChargeSummaryDto cancel(Long holderId, Long chargeId) {
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new BusinessException(CHARGE_NOT_FOUND));

        if (!charge.getHolderId().equals(holderId)) {
            throw new BusinessException(CHARGE_FORBIDDEN);
        }
        if (charge.getStatus() == CANCELED) {
            return ChargeSummaryDto.from(charge);           // 이미 취소됨 (멱등)
        }
        if (!charge.isCancelable()) {
            throw new BusinessException(CHARGE_NOT_CANCELABLE);
        }

        WalletTransaction chargeRecord = walletTransactionRepository
                .findByIdempotencyKey(WalletTransaction.idempotencyKeyOf(PAYMENT, chargeId, CHARGE))
                .orElseThrow(() -> new BusinessException(TRANSACTION_NOT_FOUND));
        if (chargeRecord.getAmount() != charge.getAmount()) {
            throw new BusinessException(INVALID_AMOUNT);
        }

        Wallet wallet = walletSupport.getWalletByHolderId(holderId);
        ledgerRecorder.withdraw(wallet, CHARGE_CANCEL, charge.getAmount(), PAYMENT, chargeId);

        pgApproval.cancel(charge, "사용자 요청 충전 취소");

        charge.markCanceled(LocalDateTime.now());
        return ChargeSummaryDto.from(charge);
    }
}
