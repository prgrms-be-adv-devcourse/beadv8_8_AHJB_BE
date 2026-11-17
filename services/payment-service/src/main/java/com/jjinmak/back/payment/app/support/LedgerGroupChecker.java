package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.back.payment.app.dto.LedgerCheckResult;
import com.jjinmak.back.payment.domain.enums.MismatchTargetType;
import com.jjinmak.back.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.back.payment.domain.enums.MismatchType.LEDGER_GROUP_UNBALANCED;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE_CANCEL;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.WITHDRAWAL;

/**
 * 원장 대사 검사: 거래 그룹(transactionGroupId)의 금액 합 == 0.
 *
 * 지갑 사이로 돈을 옮기는 거래(결제 · 환불 · 정산 · 수수료)는 나가는 쪽(-)과 들어오는 쪽(+)을 같은 그룹으로 기록하므로
 * 그룹 안의 금액을 다 더하면 0 이다. 0 이 아니면 한쪽 기록이 빠졌거나 금액이 다르게 적힌 것이다.
 * 충전 · 충전 취소 · 출금은 상대가 원장 밖(PG, 계좌)이라 한 줄만 남으므로 이 검사에서 뺀다.
 *
 * 대상일에 기록이 생긴 그룹을 본다. 합계는 그 그룹의 전체 기록으로 낸다 (자정에 걸친 그룹이 반쪽만 잡히지 않게).
 *  - expected : 0
 *  - actual   : 그룹의 금액 합
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class LedgerGroupChecker implements LedgerInvariantChecker {

    // 상대 계정이 원장 밖이라 한 줄만 남는 유형
    private static final List<WalletTransactionType> EXTERNAL_TYPES = List.of(CHARGE, CHARGE_CANCEL, WITHDRAWAL);

    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    public LedgerCheckResult check(LocalDate targetDate) {
        LocalDateTime startAt = targetDate.atStartOfDay();
        LocalDateTime endAt = targetDate.plusDays(1).atStartOfDay();

        long checkedGroups = walletTransactionRepository.countGroupsCreatedBetween(EXTERNAL_TYPES, startAt, endAt);

        List<DetectedMismatchDto> mismatches = new ArrayList<>();
        for (WalletTransactionRepository.GroupSum group
                : walletTransactionRepository.findUnbalancedGroupsCreatedBetween(EXTERNAL_TYPES, startAt, endAt)) {
            String detail = LEDGER_GROUP_UNBALANCED.getDescription() + ". 그룹 금액 합 " + group.getTotal() + "원";
            mismatches.add(new DetectedMismatchDto(
                    LEDGER_GROUP_UNBALANCED, MismatchTargetType.LEDGER_GROUP, group.getGroupId(), null,
                    null, 0L, null, group.getTotal(), detail));
        }
        return new LedgerCheckResult(Math.toIntExact(checkedGroups), mismatches);
    }
}
