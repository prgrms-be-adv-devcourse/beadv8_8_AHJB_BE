package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.payment.out.WalletTransactionRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.boundedContext.payment.app.dto.ChargeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType.*;
import static com.jjinmak.back.boundedContext.payment.exception.WalletErrorCode.*;

@Service
@RequiredArgsConstructor
public class WalletChargeCancelUseCase {

    private final WalletSupport walletSupport;
    private final LedgerRecorder ledgerRecorder;
    private final WalletTransactionRepository walletTransactionRepository;


    /**
     * 1. 이미 취소된 건이면 아무 것도 하지 않는다 (멱등)
     * 2. 취소 대상 CHARGE 기록을 찾고, 요청 금액과 대조한다
     * 3. 지갑 차감 (잔액 부족 시 Wallet.withdrawal 이 INSUFFICIENT_BALANCE)
     * 4. 원장 CHARGE_CANCEL (-) 기록
     */
    public void cancelCharge(ChargeDto dto) {
        if (alreadyRecorded(dto.paymentId(), CHARGE_CANCEL)) {
            return;
        }

        WalletTransaction charge = findChargeOf(dto.paymentId());
        if (charge.getAmount() != dto.amount()) {
            throw new BusinessException(INVALID_AMOUNT);
        }

        Wallet wallet = walletSupport.findWalletByHolderId(dto.holderId())
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND));

        ledgerRecorder.withdraw(wallet, CHARGE_CANCEL, dto.amount(), PAYMENT, dto.paymentId());
    }


    /**
     * 보상: cancelCharge 후 PG 취소가 실패했을 때 차감액을 되돌린다.
     *
     * 1. 되돌릴 CHARGE_CANCEL 기록이 없으면 아무 것도 하지 않는다 (차감이 안 됐으니 되돌릴 것도 없음)
     * 2. 이미 되돌렸으면 아무 것도 하지 않는다 (멱등)
     * 3. 지갑 재입금 + 원장 REVERSAL (+) 기록
     */
    public void revertCancelCharge(ChargeDto dto) {
        if (!alreadyRecorded(dto.paymentId(), CHARGE_CANCEL)) {
            return;
        }
        if (alreadyRecorded(dto.paymentId(), REVERSAL)) {
            return;
        }

        Wallet wallet = walletSupport.findWalletByHolderId(dto.holderId())
                .orElseThrow(() -> new BusinessException(WALLET_NOT_FOUND));

        ledgerRecorder.deposit(wallet, REVERSAL, dto.amount(), PAYMENT, dto.paymentId());
    }


    private boolean alreadyRecorded(Long paymentId, WalletTransactionType type) {
        return walletTransactionRepository.existsByIdempotencyKey(
                WalletTransaction.idempotencyKeyOf(PAYMENT, paymentId, type));
    }

    private WalletTransaction findChargeOf(Long paymentId) {
        return walletTransactionRepository
                .findByIdempotencyKey(WalletTransaction.idempotencyKeyOf(PAYMENT, paymentId, CHARGE))
                .orElseThrow(() -> new BusinessException(TRANSACTION_NOT_FOUND));
    }
}
