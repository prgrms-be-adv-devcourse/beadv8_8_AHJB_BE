package com.jjinmak.back.boundedContext.payment.app.dto;

import com.jjinmak.back.boundedContext.payment.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.payment.domain.enums.ReferenceType;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;

import java.time.LocalDateTime;

/**
 * 원장(WalletTransaction) 한 줄. 사용자에게 보여주는 예치금 입출금 내역.
 *
 * - amount        : 부호 있는 금액. 입금 +, 출금 -
 * - balanceAfter  : 이 기록 직후의 잔액
 * - referenceType / referenceId : 이 기록을 만든 원인. PAYMENT 면 충전(Charge) id, ORDER 면 주문 id
 */
public record LedgerEntryDto(
        Long id,
        WalletTransactionType type,
        long amount,
        long balanceAfter,
        ReferenceType referenceType,
        Long referenceId,
        String transactionGroupId,
        LocalDateTime createdAt
) {
    public static LedgerEntryDto from(WalletTransaction tx) {
        return new LedgerEntryDto(
                tx.getId(),
                tx.getType(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getReferenceType(),
                tx.getReferenceId(),
                tx.getTransactionGroupId(),
                tx.getCreatedAt()
        );
    }
}
