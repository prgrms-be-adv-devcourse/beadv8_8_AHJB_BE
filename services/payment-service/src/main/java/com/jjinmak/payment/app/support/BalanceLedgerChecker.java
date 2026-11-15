package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.payment.app.dto.LedgerCheckResult;
import com.jjinmak.payment.domain.enums.MismatchTargetType;
import com.jjinmak.payment.domain.enums.WalletAccount;
import com.jjinmak.payment.out.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.payment.domain.enums.MismatchType.BALANCE_LEDGER_MISMATCH;
import static com.jjinmak.payment.domain.enums.WalletAccount.DEPOSIT;
import static com.jjinmak.payment.domain.enums.WalletAccount.SETTLEMENT;

/**
 * 원장 대사 검사: 지갑 잔액 == 그 지갑 원장 기록의 금액 합. 주머니(예치금 / 정산금)마다 따로 본다.
 *
 * 잔액은 LedgerRecorder 를 통해서만 바뀌고 그때마다 원장에 한 줄이 남으므로, 원장을 처음부터 다 더하면 지금 잔액이 나와야 한다.
 * 다르면 원장 없이 잔액만 바뀌었거나, 원장만 남고 잔액이 안 바뀐 것이다.
 *
 * 대상은 전체 지갑이다. 그날 변동이 있던 지갑만 보면 "원장을 거치지 않고 잔액만 바뀐 지갑" 을 놓친다.
 * 지금 상태를 보는 검사라 대상일을 쓰지 않는다.
 *  - 대상 식별자 : 지갑id:주머니 (예: 12:DEPOSIT). 한 지갑의 두 주머니가 따로 어긋날 수 있어서다
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
        return new LedgerCheckResult(Math.toIntExact(checkedWallets), mismatches);
    }

    private DetectedMismatchDto toMismatch(WalletRepository.BalanceGap gap, WalletAccount account) {
        String pocket = account == DEPOSIT ? "예치금" : "정산금";
        String detail = BALANCE_LEDGER_MISMATCH.getDescription()
                + ". " + pocket + " 원장 합계 " + gap.getLedgerSum() + "원, " + pocket + " 잔액 " + gap.getBalance() + "원";
        return new DetectedMismatchDto(
                BALANCE_LEDGER_MISMATCH, MismatchTargetType.WALLET, gap.getWalletId() + ":" + account, null,
                null, gap.getLedgerSum(), null, gap.getBalance(), detail);
    }
}
