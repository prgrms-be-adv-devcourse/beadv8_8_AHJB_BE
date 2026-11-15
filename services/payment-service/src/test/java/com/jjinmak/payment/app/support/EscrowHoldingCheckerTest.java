package com.jjinmak.payment.app.support;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.payment.app.dto.DetectedMismatchDto;
import com.jjinmak.payment.app.dto.LedgerCheckResult;
import com.jjinmak.payment.domain.Wallet;
import com.jjinmak.payment.domain.enums.HoldingStatus;
import com.jjinmak.payment.domain.enums.MismatchTargetType;
import com.jjinmak.payment.domain.enums.WalletType;
import com.jjinmak.payment.out.repository.HoldingRepository;
import com.jjinmak.payment.out.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.jjinmak.payment.domain.enums.HoldingStatus.CONFIRMED;
import static com.jjinmak.payment.domain.enums.HoldingStatus.HELD;
import static com.jjinmak.payment.domain.enums.MismatchType.ESCROW_HOLDING_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 원장 대사 검사: ESCROW 지갑 잔액 == HELD + CONFIRMED 홀딩의 금액 합.
 */
class EscrowHoldingCheckerTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 11, 14);
    private static final long ESCROW_WALLET_ID = 77L;

    private WalletRepository walletRepository;
    private EscrowHoldingChecker checker;

    // 홀딩 합을 조회할 때 넘어온 상태 목록
    private final List<Collection<HoldingStatus>> askedStatuses = new ArrayList<>();
    private long holdingSum;

    @BeforeEach
    void setUp() {
        walletRepository = mock(WalletRepository.class);
        HoldingRepository holdingRepository = mock(HoldingRepository.class);
        when(holdingRepository.sumAmountByStatusIn(any())).thenAnswer(invocation -> {
            askedStatuses.add(invocation.getArgument(0));
            return holdingSum;
        });
        checker = new EscrowHoldingChecker(new SystemWalletFinder(walletRepository), holdingRepository);
    }

    private void escrowBalance(long balance) {
        Wallet escrow = Wallet.createSystemWallet(WalletType.ESCROW);
        ReflectionTestUtils.setField(escrow, "id", ESCROW_WALLET_ID);
        if (balance > 0) {
            escrow.deposit(balance);
        }
        when(walletRepository.findByTypeAndHolderIsNull(WalletType.ESCROW)).thenReturn(Optional.of(escrow));
    }

    @Test
    @DisplayName("ESCROW 잔액과 미정산 홀딩 합이 같으면 일치. 홀딩은 HELD 와 CONFIRMED 만 더한다")
    void matched() {
        escrowBalance(50_000);
        holdingSum = 50_000;

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.mismatches()).isEmpty();
        assertThat(askedStatuses).hasSize(1);
        assertThat(askedStatuses.get(0)).containsExactly(HELD, CONFIRMED);
    }

    @Test
    @DisplayName("홀딩이 하나도 없고 ESCROW 도 비어 있으면 일치")
    void matchedWhenBothEmpty() {
        escrowBalance(0);
        holdingSum = 0;

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("ESCROW 잔액이 홀딩 합과 다르면 불일치. 기준은 홀딩 합, 우리 값은 ESCROW 잔액")
    void mismatched() {
        escrowBalance(40_000);
        holdingSum = 50_000;

        LedgerCheckResult result = checker.check(TARGET_DATE);

        assertThat(result.checkedCount()).isEqualTo(1);
        assertThat(result.mismatches()).hasSize(1);
        DetectedMismatchDto mismatch = result.mismatches().get(0);
        assertThat(mismatch.type()).isEqualTo(ESCROW_HOLDING_MISMATCH);
        assertThat(mismatch.targetType()).isEqualTo(MismatchTargetType.WALLET);
        assertThat(mismatch.targetKey()).isEqualTo("77");
        assertThat(mismatch.pgOrderId()).isNull();
        assertThat(mismatch.expectedAmount()).isEqualTo(50_000L);
        assertThat(mismatch.actualAmount()).isEqualTo(40_000L);
        assertThat(mismatch.detail()).contains("50000", "40000");
    }

    @Test
    @DisplayName("ESCROW 지갑을 찾지 못하면 불일치가 아니라 예외 (확인하지 못한 것)")
    void failsWhenEscrowWalletMissing() {
        holdingSum = 50_000;

        assertThatThrownBy(() -> checker.check(TARGET_DATE)).isInstanceOf(BusinessException.class);
    }
}
