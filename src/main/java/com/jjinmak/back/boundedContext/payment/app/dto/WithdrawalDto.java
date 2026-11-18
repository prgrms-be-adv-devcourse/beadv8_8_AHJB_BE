package com.jjinmak.back.boundedContext.payment.app.dto;

import com.jjinmak.back.boundedContext.payment.domain.Withdrawal;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletAccount;
import com.jjinmak.back.boundedContext.payment.domain.enums.WithdrawalStatus;

import java.time.LocalDateTime;

/** 출금 1건. account 는 어느 잔액(예치금/정산금)에서 나갔는지 */
public record WithdrawalDto(
        Long withdrawalId,
        long amount,
        WalletAccount account,
        WithdrawalStatus status,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        LocalDateTime failedAt,
        String failureReason
) {
    public static WithdrawalDto from(Withdrawal w) {
        return new WithdrawalDto(
                w.getId(),
                w.getAmount(),
                w.getAccount(),
                w.getStatus(),
                w.getCreatedAt(),
                w.getCompletedAt(),
                w.getFailedAt(),
                w.getFailureReason()
        );
    }
}
