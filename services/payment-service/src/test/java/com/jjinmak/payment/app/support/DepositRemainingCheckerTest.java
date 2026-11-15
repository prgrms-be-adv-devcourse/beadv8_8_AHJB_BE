package com.jjinmak.payment.app.support;

import com.jjinmak.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.payment.app.dto.LedgerCheckResult;
import com.jjinmak.payment.domain.enums.ChargeStatus;
import com.jjinmak.payment.domain.enums.MismatchTargetType;
import com.jjinmak.payment.out.repository.ChargeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.payment.domain.enums.ChargeStatus.CHARGED;
import static com.jjinmak.payment.domain.enums.MismatchType.DEPOSIT_REMAINING_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 원장 대사 검사: 지갑 예치금 >= 충전 건들의 미사용 금액 합.
 * 어긋난 지갑을 고르는 것은 DB 쿼리이고, 여기서는 그 결과를 불일치로 옮기는 부분을 본다.
 */
class DepositRemainingCheckerTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 11, 14);

    private DepositRemainingChecker checker;

    private long walletsWithRemaining;
    private final List<ChargeRepository.RemainingGap> gaps = new ArrayList<>();
    // 조회할 때 넘어온 충전 상태
    private final List<ChargeStatus> askedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ChargeRepository chargeRepository = mock(ChargeRepository.class);
        when(chargeRepository.countWalletsWithRemaining(any())).thenAnswer(invocation -> {
            askedStatuses.add(invocation.getArgument(0));
            return walletsWithRemaining;
        });
        when(chargeRepository.findWalletsWithRemainingOverBalance(any())).thenAnswer(invocation -> {
            askedStatuses.add(invocation.getArgument(0));
            return new ArrayList<>(gaps);
        });
        checker = new DepositRemainingChecker(chargeRepository);
    }

    private static ChargeRepository.RemainingGap gap(long walletId, long balance, long remainingSum) {
        return new ChargeRepository.RemainingGap() {
            @Override
            public Long getWalletId() {
                return walletId;
            }

            @Override
            public Long getBalance() {
                return balance;
            }

            @Override
            public Long getRemainingSum() {
                return remainingSum;
            }
        };
    }

    @Test
    @DisplayName("어긋난 지갑이 없으면 일치. 비교 건수는 미사용 충전 금액이 있는 지갑 수이고 CHARGED 충전만 본다")
    void matched() {
        walletsWithRemaining = 12;

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(12);
        assertThat(result.mismatches()).isEmpty();
        assertThat(askedStatuses).containsExactly(CHARGED, CHARGED);
    }

    @Test
    @DisplayName("미사용 충전 금액 합이 예치금보다 큰 지갑은 지갑마다 불일치 1건")
    void mismatched() {
        walletsWithRemaining = 12;
        gaps.add(gap(3L, 7_000, 10_000));
        gaps.add(gap(9L, 0, 5_000));

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(12);
        assertThat(result.mismatches()).hasSize(2);

        DetectedMismatchDto first = result.mismatches().get(0);
        assertThat(first.type()).isEqualTo(DEPOSIT_REMAINING_MISMATCH);
        assertThat(first.targetType()).isEqualTo(MismatchTargetType.WALLET);
        assertThat(first.targetKey()).isEqualTo("3");
        assertThat(first.pgOrderId()).isNull();
        assertThat(first.expectedAmount()).isEqualTo(10_000L);    // 미사용 충전 금액 합
        assertThat(first.actualAmount()).isEqualTo(7_000L);       // 예치금
        assertThat(first.detail()).contains("10000", "7000");

        DetectedMismatchDto second = result.mismatches().get(1);
        assertThat(second.targetKey()).isEqualTo("9");
        assertThat(second.expectedAmount()).isEqualTo(5_000L);
        assertThat(second.actualAmount()).isEqualTo(0L);
    }
}
