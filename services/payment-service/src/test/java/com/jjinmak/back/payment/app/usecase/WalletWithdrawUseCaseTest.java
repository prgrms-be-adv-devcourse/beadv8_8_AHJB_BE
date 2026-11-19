package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.dto.WithdrawalDto;
import com.jjinmak.back.payment.app.support.DepositConsumer;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.Withdrawal;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import com.jjinmak.back.payment.out.repository.WithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.WITHDRAWAL;
import static com.jjinmak.back.payment.exception.WalletErrorCode.NOTHING_TO_WITHDRAW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 예치금 전액 출금과 포인트. 포인트는 출금 대상이 아니다.
 */
class WalletWithdrawUseCaseTest {

    private static final long HOLDER_ID = 1L;

    private WalletWithdrawUseCase useCase;
    private final List<WalletTransaction> ledger = new ArrayList<>();

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet(null, WalletType.USER);

        WalletSupport walletSupport = mock(WalletSupport.class);
        when(walletSupport.getWalletByHolderId(HOLDER_ID)).thenReturn(wallet);

        WithdrawalRepository withdrawalRepository = mock(WithdrawalRepository.class);
        when(withdrawalRepository.save(any(Withdrawal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransactionRepository walletTransactionRepository = mock(WalletTransactionRepository.class);
        when(walletTransactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> {
            WalletTransaction row = invocation.getArgument(0);
            ledger.add(row);
            return row;
        });

        useCase = new WalletWithdrawUseCase(
                walletSupport, withdrawalRepository, mock(DepositConsumer.class), new LedgerRecorder(walletTransactionRepository));
    }

    @Test
    @DisplayName("전액 출금은 예치금만 0 으로 만들고 포인트는 그대로 둔다")
    void withdrawLeavesPoint() {
        wallet.deposit(10_000);
        wallet.depositPoint(1_500);

        WithdrawalDto result = useCase.withdrawAll(HOLDER_ID);

        assertThat(result.amount()).isEqualTo(10_000);                   // 출금액에 포인트가 섞이지 않는다
        assertThat(wallet.getBalance()).isZero();
        assertThat(wallet.getPointBalance()).isEqualTo(1_500);

        assertThat(ledger).hasSize(1);
        WalletTransaction row = ledger.get(0);
        assertThat(row.getAccount()).isEqualTo(WalletAccount.DEPOSIT);
        assertThat(row.getType()).isEqualTo(WITHDRAWAL);
        assertThat(row.getAmount()).isEqualTo(-10_000);
    }

    @Test
    @DisplayName("포인트만 있고 예치금이 없으면 출금할 것이 없다")
    void pointOnlyIsNotWithdrawable() {
        wallet.depositPoint(1_500);

        assertThatThrownBy(() -> useCase.withdrawAll(HOLDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(NOTHING_TO_WITHDRAW);

        assertThat(wallet.getPointBalance()).isEqualTo(1_500);
        assertThat(ledger).isEmpty();
    }
}
