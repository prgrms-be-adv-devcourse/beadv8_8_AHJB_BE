package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.back.payment.app.dto.LedgerCheckResult;
import com.jjinmak.back.payment.app.support.BalanceLedgerChecker;
import com.jjinmak.back.payment.domain.enums.MismatchTargetType;
import com.jjinmak.back.payment.out.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.back.payment.domain.enums.MismatchType.BALANCE_LEDGER_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 원장 대사 검사: 지갑 잔액 == 원장 합계 (예치금 / 정산금 / 포인트 따로).
 * 어긋난 지갑을 고르는 것은 DB 쿼리이고, 여기서는 그 결과를 불일치로 옮기는 부분을 본다.
 */
class BalanceLedgerCheckerTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 11, 14);

    private BalanceLedgerChecker checker;

    private long walletCount;
    private final List<WalletRepository.BalanceGap> depositGaps = new ArrayList<>();
    private final List<WalletRepository.BalanceGap> settlementGaps = new ArrayList<>();
    private final List<WalletRepository.BalanceGap> pointGaps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        WalletRepository walletRepository = mock(WalletRepository.class);
        when(walletRepository.count()).thenAnswer(invocation -> walletCount);
        when(walletRepository.findDepositLedgerGaps()).thenAnswer(invocation -> new ArrayList<>(depositGaps));
        when(walletRepository.findSettlementLedgerGaps()).thenAnswer(invocation -> new ArrayList<>(settlementGaps));
        when(walletRepository.findPointLedgerGaps()).thenAnswer(invocation -> new ArrayList<>(pointGaps));
        checker = new BalanceLedgerChecker(walletRepository);
    }

    private static WalletRepository.BalanceGap gap(long walletId, long balance, long ledgerSum) {
        return new WalletRepository.BalanceGap() {
            @Override
            public Long getWalletId() {
                return walletId;
            }

            @Override
            public Long getBalance() {
                return balance;
            }

            @Override
            public Long getLedgerSum() {
                return ledgerSum;
            }
        };
    }

    @Test
    @DisplayName("어긋난 지갑이 없으면 일치. 비교 건수는 전체 지갑 수")
    void matched() {
        walletCount = 250;

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(250);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("예치금이 원장 합계와 다르면 불일치. 대상 식별자는 지갑id:DEPOSIT, 기준은 원장 합계")
    void depositMismatched() {
        walletCount = 250;
        depositGaps.add(gap(12L, 30_000, 25_000));

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.mismatches()).hasSize(1);
        DetectedMismatchDto mismatch = result.mismatches().get(0);
        assertThat(mismatch.type()).isEqualTo(BALANCE_LEDGER_MISMATCH);
        assertThat(mismatch.targetType()).isEqualTo(MismatchTargetType.WALLET);
        assertThat(mismatch.targetKey()).isEqualTo("12:DEPOSIT");
        assertThat(mismatch.pgOrderId()).isNull();
        assertThat(mismatch.expectedAmount()).isEqualTo(25_000L);   // 원장 합계
        assertThat(mismatch.actualAmount()).isEqualTo(30_000L);     // 지갑 잔액
        assertThat(mismatch.detail()).contains("예치금", "25000", "30000");
    }

    @Test
    @DisplayName("한 지갑의 예치금과 정산금이 모두 어긋나면 주머니마다 따로 불일치 2건")
    void bothPocketsMismatched() {
        walletCount = 250;
        depositGaps.add(gap(12L, 30_000, 25_000));
        settlementGaps.add(gap(12L, 0, 9_000));

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(250);
        assertThat(result.mismatches()).hasSize(2);
        assertThat(result.mismatches().get(0).targetKey()).isEqualTo("12:DEPOSIT");

        DetectedMismatchDto settlement = result.mismatches().get(1);
        assertThat(settlement.targetKey()).isEqualTo("12:SETTLEMENT");
        assertThat(settlement.expectedAmount()).isEqualTo(9_000L);
        assertThat(settlement.actualAmount()).isEqualTo(0L);
        assertThat(settlement.detail()).contains("정산금");
    }

    @Test
    @DisplayName("포인트가 원장 합계와 다르면 불일치. 대상 식별자는 지갑id:POINT, 기준은 원장 합계")
    void pointMismatched() {
        walletCount = 250;
        pointGaps.add(gap(12L, 1_500, 1_000));

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(250);
        assertThat(result.mismatches()).hasSize(1);
        DetectedMismatchDto mismatch = result.mismatches().get(0);
        assertThat(mismatch.type()).isEqualTo(BALANCE_LEDGER_MISMATCH);
        assertThat(mismatch.targetKey()).isEqualTo("12:POINT");
        assertThat(mismatch.expectedAmount()).isEqualTo(1_000L);    // 원장 합계
        assertThat(mismatch.actualAmount()).isEqualTo(1_500L);      // 지갑 포인트
        assertThat(mismatch.detail()).contains("포인트", "1000", "1500");
    }

    @Test
    @DisplayName("세 주머니가 모두 어긋나면 예치금 · 정산금 · 포인트 순서로 불일치 3건")
    void allPocketsMismatched() {
        walletCount = 250;
        depositGaps.add(gap(12L, 30_000, 25_000));
        settlementGaps.add(gap(12L, 0, 9_000));
        pointGaps.add(gap(12L, 300, 0));

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.mismatches()).extracting(DetectedMismatchDto::targetKey)
                .containsExactly("12:DEPOSIT", "12:SETTLEMENT", "12:POINT");
    }
}
