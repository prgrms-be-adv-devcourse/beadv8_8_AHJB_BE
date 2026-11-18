package com.jjinmak.back.payment.app.support;

import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import com.jjinmak.back.payment.out.toss.TossPaymentsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.back.payment.app.support.PgApprovalSupport.METHOD_NOT_ALLOWED;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 승인 단계를 끝내지 못한 충전의 복구(복구 스케줄러 · 웹훅 공통)에서도 결제수단을 승인 단계와 똑같이 검증한다.
 */
class ChargeRecoverySupportTest {

    private static final long HOLDER_ID = 1L;
    private static final long AMOUNT = 30_000;

    private TossPaymentsClient toss;
    private ChargeRecoverySupport support;
    private final List<WalletTransaction> ledger = new ArrayList<>();

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet(null, WalletType.USER);

        WalletSupport walletSupport = mock(WalletSupport.class);
        when(walletSupport.getWalletByHolderId(HOLDER_ID)).thenReturn(wallet);

        WalletTransactionRepository walletTransactionRepository = mock(WalletTransactionRepository.class);
        when(walletTransactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> {
            WalletTransaction row = invocation.getArgument(0);
            ledger.add(row);
            return row;
        });

        toss = mock(TossPaymentsClient.class);
        support = new ChargeRecoverySupport(walletSupport, new LedgerRecorder(walletTransactionRepository), toss);
    }

    private Charge manualCharge() {
        Charge charge = Charge.requestManual(wallet, HOLDER_ID, AMOUNT);
        ReflectionTestUtils.setField(charge, "id", 11L);
        return charge;
    }

    private Charge paymentCharge() {
        Payment payment = Payment.create(100L, HOLDER_ID, wallet, 50_000);
        payment.decideCharge(AMOUNT);
        Charge charge = Charge.requestForPayment(wallet, payment);
        ReflectionTestUtils.setField(charge, "id", 12L);
        return charge;
    }

    private static TossPaymentResponse done(Charge charge, String method) {
        return new TossPaymentResponse("pk-" + charge.getId(), charge.getPgOrderId(), "찐막 예치금 충전", "DONE",
                charge.getAmount(), charge.getAmount(), method, null, "2026-11-18T10:00:00+09:00", null, null, null);
    }

    @Test
    @DisplayName("계좌이체로 승인된 수동 충전은 예치금에 반영해 CHARGED 로 복구한다")
    void manualTransferIsRecovered() {
        Charge charge = manualCharge();

        boolean changed = support.recover(charge, done(charge, "계좌이체"));

        assertThat(changed).isTrue();
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CHARGED);
        assertThat(charge.getMethod()).isEqualTo("계좌이체");
        assertThat(wallet.getBalance()).isEqualTo(AMOUNT);
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).getType()).isEqualTo(CHARGE);
        verify(toss, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("카드로 승인된 수동 충전은 예치금에 넣지 않고 토스 결제를 취소한 뒤 ABORTED 로 닫는다")
    void manualCardIsCanceled() {
        Charge charge = manualCharge();

        boolean changed = support.recover(charge, done(charge, "카드"));

        assertThat(changed).isTrue();
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.ABORTED);
        assertThat(charge.getFailureCode()).isEqualTo(METHOD_NOT_ALLOWED);
        assertThat(charge.getFailureMessage()).contains("카드");
        assertThat(wallet.getBalance()).isZero();
        assertThat(ledger).isEmpty();
        verify(toss).cancel(eq("pk-11"), anyString(), contains(charge.getPgOrderId()));
    }

    @Test
    @DisplayName("토스 취소가 실패하면 충전 건을 닫지 않고 예외를 올린다 (다음 복구 때 다시 시도)")
    void cancelFailureKeepsChargeRequested() {
        Charge charge = manualCharge();
        when(toss.cancel(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("토스 통신 실패"));

        assertThatThrownBy(() -> support.recover(charge, done(charge, "카드")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.REQUESTED);
        assertThat(wallet.getBalance()).isZero();
        assertThat(ledger).isEmpty();
    }

    @Test
    @DisplayName("부족분 충전은 결제수단과 관계없이 취소되지만, 허용되지 않은 수단이면 그 사유로 닫힌다")
    void paymentChargeWithCardIsClosedWithMethodReason() {
        Charge charge = paymentCharge();

        support.recover(charge, done(charge, "간편결제"));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.ABORTED);
        assertThat(charge.getFailureCode()).isEqualTo(METHOD_NOT_ALLOWED);
        assertThat(wallet.getBalance()).isZero();
        verify(toss).cancel(eq("pk-12"), anyString(), anyString());
    }

    @Test
    @DisplayName("계좌이체로 승인된 부족분 충전은 기존대로 주문 흐름이 깨진 것으로 보고 취소한다")
    void paymentChargeWithTransferKeepsExistingPolicy() {
        Charge charge = paymentCharge();

        support.recover(charge, done(charge, "계좌이체"));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.ABORTED);
        assertThat(charge.getFailureCode()).isEqualTo("RECOVERED_CANCELED");
        assertThat(wallet.getBalance()).isZero();
        verify(toss).cancel(eq("pk-12"), anyString(), anyString());
    }
}
