package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.dto.WithdrawalDto;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.Withdrawal;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.domain.enums.WithdrawalStatus;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import com.jjinmak.back.payment.out.repository.WithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.back.payment.domain.enums.ReferenceType.WITHDRAWAL;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.SETTLEMENT_WITHDRAWAL;
import static com.jjinmak.back.payment.exception.WalletErrorCode.NOTHING_SETTLEMENT_TO_WITHDRAW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 정산금 전액 출금. 예치금과 포인트는 출금 대상이 아니다.
 */
class WalletWithdrawSettlementUseCaseTest {

    private static final long HOLDER_ID = 1L;

    private WalletWithdrawSettlementUseCase useCase;
    private final List<WalletTransaction> ledger = new ArrayList<>();
    private final List<Withdrawal> withdrawals = new ArrayList<>();

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet(null, WalletType.USER);

        WalletSupport walletSupport = mock(WalletSupport.class);
        when(walletSupport.getWalletByHolderId(HOLDER_ID)).thenReturn(wallet);

        WithdrawalRepository withdrawalRepository = mock(WithdrawalRepository.class);
        when(withdrawalRepository.save(any(Withdrawal.class))).thenAnswer(invocation -> {
            Withdrawal withdrawal = invocation.getArgument(0);
            withdrawals.add(withdrawal);
            return withdrawal;
        });

        WalletTransactionRepository walletTransactionRepository = mock(WalletTransactionRepository.class);
        when(walletTransactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> {
            WalletTransaction row = invocation.getArgument(0);
            ledger.add(row);
            return row;
        });

        useCase = new WalletWithdrawSettlementUseCase(
                walletSupport, withdrawalRepository, new LedgerRecorder(walletTransactionRepository));
    }

    @Test
    @DisplayName("정산금 전액 출금은 정산금만 0 으로 만들고 예치금 · 포인트는 그대로 둔다")
    void withdrawOnlySettlement() {
        wallet.deposit(10_000);
        wallet.depositSettlement(18_000);
        wallet.depositPoint(1_500);

        WithdrawalDto result = useCase.withdrawAll(HOLDER_ID);

        assertThat(result.amount()).isEqualTo(18_000);
        assertThat(result.account()).isEqualTo(WalletAccount.SETTLEMENT);
        assertThat(result.status()).isEqualTo(WithdrawalStatus.COMPLETED);
        assertThat(result.completedAt()).isNotNull();

        assertThat(wallet.getSettlementBalance()).isZero();
        assertThat(wallet.getBalance()).isEqualTo(10_000);
        assertThat(wallet.getPointBalance()).isEqualTo(1_500);

        assertThat(withdrawals).hasSize(1);
        assertThat(withdrawals.get(0).getHolderId()).isEqualTo(HOLDER_ID);

        assertThat(ledger).hasSize(1);
        WalletTransaction row = ledger.get(0);
        assertThat(row.getAccount()).isEqualTo(WalletAccount.SETTLEMENT);
        assertThat(row.getType()).isEqualTo(SETTLEMENT_WITHDRAWAL);
        assertThat(row.getAmount()).isEqualTo(-18_000);
        assertThat(row.getBalanceAfter()).isZero();
        assertThat(row.getReferenceType()).isEqualTo(WITHDRAWAL);
    }

    @Test
    @DisplayName("정산금이 없으면 예치금 · 포인트가 있어도 출금할 것이 없다")
    void nothingToWithdraw() {
        wallet.deposit(10_000);
        wallet.depositPoint(1_500);

        assertThatThrownBy(() -> useCase.withdrawAll(HOLDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(NOTHING_SETTLEMENT_TO_WITHDRAW);

        assertThat(wallet.getBalance()).isEqualTo(10_000);
        assertThat(wallet.getPointBalance()).isEqualTo(1_500);
        assertThat(withdrawals).isEmpty();
        assertThat(ledger).isEmpty();
    }
}
