package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.payment.app.dto.LedgerCheckResult;
import com.jjinmak.payment.domain.enums.MismatchTargetType;
import com.jjinmak.payment.out.repository.ChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.payment.domain.enums.ChargeStatus.CHARGED;
import static com.jjinmak.payment.domain.enums.MismatchType.DEPOSIT_REMAINING_MISMATCH;

/**
 * 원장 대사 검사: 지갑 예치금 >= 그 지갑 충전 건들의 미사용 금액(remainingAmount) 합.
 *
 * 예치금 = 환불로 들어온 돈 + 충전하고 아직 안 쓴 돈 이므로, 미사용 충전 금액의 합이 예치금보다 클 수 없다.
 * 크다면 예치금은 빠져나갔는데 충전 건의 미사용 금액이 줄지 않은 것이고, 그 충전을 취소하면 없는 돈을 환불하게 된다.
 * (선입선출 소비는 DepositConsumer 참고. 미사용 금액이 남는 상태는 CHARGED 뿐이다)
 *
 * 지금 상태를 보는 검사라 대상일을 쓰지 않는다.
 *  - expected : 미사용 충전 금액 합 (예치금이 최소한 이만큼은 있어야 한다)
 *  - actual   : 지갑 예치금
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DepositRemainingChecker implements LedgerInvariantChecker {

    private final ChargeRepository chargeRepository;

    @Override
    public LedgerCheckResult check(LocalDate targetDate) {
        long checkedWallets = chargeRepository.countWalletsWithRemaining(CHARGED);

        List<DetectedMismatchDto> mismatches = new ArrayList<>();
        for (ChargeRepository.RemainingGap gap : chargeRepository.findWalletsWithRemainingOverBalance(CHARGED)) {
            String detail = DEPOSIT_REMAINING_MISMATCH.getDescription()
                    + ". 미사용 충전 금액 합 " + gap.getRemainingSum() + "원, 예치금 " + gap.getBalance() + "원";
            mismatches.add(new DetectedMismatchDto(
                    DEPOSIT_REMAINING_MISMATCH, MismatchTargetType.WALLET, String.valueOf(gap.getWalletId()), null,
                    null, gap.getRemainingSum(), null, gap.getBalance(), detail));
        }
        return new LedgerCheckResult(Math.toIntExact(checkedWallets), mismatches);
    }
}
