package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.back.payment.app.dto.LedgerCheckResult;
import com.jjinmak.back.payment.domain.enums.MismatchTargetType;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.out.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.back.payment.domain.enums.MismatchType.BALANCE_LEDGER_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.WalletAccount.DEPOSIT;
import static com.jjinmak.back.payment.domain.enums.WalletAccount.POINT;
import static com.jjinmak.back.payment.domain.enums.WalletAccount.SETTLEMENT;

/**
 * 원장 대사 검사: 지갑 잔액 == 그 지갑 원장 기록의 금액 합. 주머니(예치금 / 정산금 / 포인트)마다 따로 확인
 *
 *  - expected   : 원장 합계
 *  - actual     : 지갑 잔액
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BalanceLedgerChecker implements LedgerInvariantChecker {

    private final WalletRepository walletRepository;

    @Override
    public LedgerCheckResult check(LocalDate targetDate) {
        long checkedWallets = walletRepository.count();

        List<DetectedMismatchDto> mismatches = new ArrayList<>();
        for (WalletRepository.BalanceGap gap : walletRepository.findDepositLedgerGaps()) {
            mismatches.add(toMismatch(gap, DEPOSIT));
        }
        for (WalletRepository.BalanceGap gap : walletRepository.findSettlementLedgerGaps()) {
            mismatches.add(toMismatch(gap, SETTLEMENT));
        }
        for (WalletRepository.BalanceGap gap : walletRepository.findPointLedgerGaps()) {
            mismatches.add(toMismatch(gap, POINT));
        }
        return new LedgerCheckResult(Math.toIntExact(checkedWallets), mismatches);
    }

    private DetectedMismatchDto toMismatch(WalletRepository.BalanceGap gap, WalletAccount account) {
        String pocket = switch (account) {
            case DEPOSIT -> "예치금";
            case SETTLEMENT -> "정산금";
            case POINT -> "포인트";
        };
        String detail = BALANCE_LEDGER_MISMATCH.getDescription()
                + ". " + pocket + " 원장 합계 " + gap.getLedgerSum() + "원, " + pocket + " 잔액 " + gap.getBalance() + "원";
        return new DetectedMismatchDto(
                BALANCE_LEDGER_MISMATCH, MismatchTargetType.WALLET, gap.getWalletId() + ":" + account, null,
                null, gap.getLedgerSum(), null, gap.getBalance(), detail);
    }
}
