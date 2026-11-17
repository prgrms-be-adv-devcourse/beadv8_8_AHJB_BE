package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.back.payment.app.dto.LedgerCheckResult;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.payment.domain.enums.MismatchTargetType;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.jjinmak.back.payment.domain.enums.HoldingStatus.CONFIRMED;
import static com.jjinmak.back.payment.domain.enums.HoldingStatus.HELD;
import static com.jjinmak.back.payment.domain.enums.MismatchType.ESCROW_HOLDING_MISMATCH;

/**
 * 원장 대사 검사: ESCROW 지갑 잔액 == 아직 정산 · 환불되지 않은 홀딩의 금액 합.
 *
 * 결제하면 구매자 돈이 ESCROW 로 들어오고, 정산(RELEASED)이나 환불(REFUNDED) 때 나간다.
 * 구매 확정(CONFIRMED)은 상태만 바꾸고 돈은 그대로 두므로, ESCROW 에 남아 있는 돈은 HELD + CONFIRMED 홀딩의 합이다.
 *
 * 지금 상태를 보는 검사라 대상일을 쓰지 않는다.
 *  - expected : 홀딩 합 (ESCROW 에 있어야 하는 금액)
 *  - actual   : ESCROW 지갑 잔액
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class EscrowHoldingChecker implements LedgerInvariantChecker {

    // 돈이 아직 ESCROW 에 있는 홀딩 상태
    private static final List<HoldingStatus> IN_ESCROW = List.of(HELD, CONFIRMED);

    private final SystemWalletFinder systemWalletFinder;
    private final HoldingRepository holdingRepository;

    @Override
    public LedgerCheckResult check(LocalDate targetDate) {
        Wallet escrow = systemWalletFinder.escrow();
        long holdingSum = holdingRepository.sumAmountByStatusIn(IN_ESCROW);

        if (escrow.getBalance() == holdingSum) {
            return new LedgerCheckResult(1, List.of());
        }

        String detail = ESCROW_HOLDING_MISMATCH.getDescription()
                + ". 미정산 홀딩 합 " + holdingSum + "원, ESCROW 잔액 " + escrow.getBalance() + "원";
        return new LedgerCheckResult(1, List.of(new DetectedMismatchDto(
                ESCROW_HOLDING_MISMATCH, MismatchTargetType.WALLET, String.valueOf(escrow.getId()), null,
                null, holdingSum, null, escrow.getBalance(), detail)));
    }
}
