package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.dto.HoldDto;
import com.jjinmak.back.payment.app.dto.PaymentSummaryDto;
import com.jjinmak.back.payment.app.support.HoldingCreator;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.app.support.PgApprovalSupport;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentPlan;
import com.jjinmak.back.payment.domain.PaymentShare;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_PAYMENT_MISMATCH;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_SUPERSEDED;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_BALANCE;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_POINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 주문 결제 승인: 직접 결제와 부족분 충전의 반영 차이, 토스 승인 전 검증, 승인 뒤 반영 실패 시 보상.
 */
class PaymentConfirmUseCaseTest {

    private static final long HOLDER_ID = 1L;
    private static final long TOTAL = 30_000L;
    private static final String PAYMENT_KEY = "pk-1";

    private PaymentConfirmUseCase useCase;
    private ChargeRepository chargeRepository;
    private HoldingCreator holdingCreator;
    private PgApprovalSupport pgApproval;
    private WalletTransactionRepository walletTransactionRepository;

    private Wallet wallet;
    private Payment payment;

    @BeforeEach
    void setUp() {
        wallet = new Wallet(null, WalletType.USER);
        payment = Payment.create(100L, HOLDER_ID, wallet, TOTAL);
        payment.addItem(1_001L, 2L, TOTAL, 0);
        ReflectionTestUtils.setField(payment, "id", 7L);

        WalletSupport walletSupport = mock(WalletSupport.class);
        when(walletSupport.getWalletByHolderId(HOLDER_ID)).thenReturn(wallet);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findWithItemsById(7L)).thenReturn(Optional.of(payment));

        chargeRepository = mock(ChargeRepository.class);
        holdingCreator = mock(HoldingCreator.class);
        pgApproval = mock(PgApprovalSupport.class);
        walletTransactionRepository = mock(WalletTransactionRepository.class);

        useCase = new PaymentConfirmUseCase(paymentRepository, chargeRepository, walletSupport,
                new LedgerRecorder(walletTransactionRepository), holdingCreator, pgApproval, mock(EventPublisher.class));
    }

    // 포인트 pointAmount + 나머지를 카드로 직접 결제
    private Charge directCharge(long pointAmount) {
        long pg = TOTAL - pointAmount;
        payment.decide(new PaymentPlan(PayMethod.CARD, pointAmount, 0L, pg, pg, 0L, false));
        return register(Charge.requestDirectPayment(wallet, payment, PayMethod.CARD.pgMethod()));
    }

    // 예치금 결제의 부족분 충전
    private Charge topUpCharge(long chargeAmount) {
        payment.decideCharge(chargeAmount);
        return register(Charge.requestForPayment(wallet, payment));
    }

    private Charge register(Charge charge) {
        ReflectionTestUtils.setField(charge, "id", 11L + chargeSeq++);
        when(chargeRepository.findByPgOrderId(charge.getPgOrderId())).thenReturn(Optional.of(charge));
        return charge;
    }

    private long chargeSeq = 0;

    private void tossApproves(Charge charge, String method) {
        when(pgApproval.approve(charge, PAYMENT_KEY)).thenReturn(new TossPaymentResponse(
                PAYMENT_KEY, charge.getPgOrderId(), "찐막 주문 결제", "DONE", charge.getAmount(), charge.getAmount(),
                method, null, "2026-11-18T10:00:00+09:00", null, null, null));
    }

    @Test
    @DisplayName("직접 결제 승인: 예치금에 넣지 않고 결제 건을 USED 로, 실제 결제수단과 함께 PAID 로 만든다")
    void directPaymentDoesNotTouchDeposit() {
        wallet.depositPoint(5_000);
        Charge charge = directCharge(5_000);
        tossApproves(charge, "간편결제");

        PaymentSummaryDto result = useCase.confirm(HOLDER_ID, charge.getPgOrderId(), PAYMENT_KEY, 25_000);

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.pgMethod()).isEqualTo("간편결제");
        assertThat(result.pgAmount()).isEqualTo(25_000);
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.USED);
        assertThat(charge.getRemainingAmount()).isZero();
        assertThat(wallet.getBalance()).isZero();                       // CHARGE 입금이 없다
        verify(walletTransactionRepository, never()).save(any());
        verify(holdingCreator).hold(argThat((HoldDto dto) -> dto.share().equals(new PaymentShare(5_000, 0, 25_000))));
        verify(holdingCreator, never()).hold(any(HoldDto.class), any(Charge.class));
    }

    @Test
    @DisplayName("부족분 충전 승인: 충전 금액을 예치금에 넣은 뒤 그 충전 건을 먼저 써서 홀딩한다")
    void topUpDepositsThenHolds() {
        wallet.deposit(18_000);
        Charge charge = topUpCharge(12_000);
        tossApproves(charge, "계좌이체");

        PaymentSummaryDto result = useCase.confirm(HOLDER_ID, charge.getPgOrderId(), PAYMENT_KEY, 12_000);

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.pgMethod()).isNull();                         // 직접 결제가 아니다
        assertThat(wallet.getBalance()).isEqualTo(30_000);              // 홀딩은 가짜라 차감 전
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.USED);
        verify(holdingCreator).hold(any(HoldDto.class), eq(charge));
    }

    @Test
    @DisplayName("토스 승인 뒤 홀딩이 실패하면 토스 결제를 취소하고 예외를 그대로 올린다")
    void holdFailureAfterApprovalIsCompensated() {
        wallet.depositPoint(5_000);
        Charge charge = directCharge(5_000);
        tossApproves(charge, "카드");
        doThrow(new BusinessException(INSUFFICIENT_POINT)).when(holdingCreator).hold(any(HoldDto.class));

        assertThatThrownBy(() -> useCase.confirm(HOLDER_ID, charge.getPgOrderId(), PAYMENT_KEY, 25_000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(INSUFFICIENT_POINT);

        verify(pgApproval).cancelApproved(eq(charge), eq(PAYMENT_KEY), anyString());
        assertThat(payment.isPaid()).isFalse();
    }

    @Test
    @DisplayName("결제창에 있는 동안 포인트가 줄었으면 토스 승인 전에 INSUFFICIENT_POINT")
    void pointDecreasedBeforeApproval() {
        wallet.depositPoint(5_000);
        Charge charge = directCharge(5_000);
        wallet.usePoint(1);

        assertThatThrownBy(() -> useCase.confirm(HOLDER_ID, charge.getPgOrderId(), PAYMENT_KEY, 25_000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(INSUFFICIENT_POINT);

        verify(pgApproval, never()).approve(any(), anyString());
    }

    @Test
    @DisplayName("결제창에 있는 동안 예치금이 줄어 충전해도 모자라면 토스 승인 전에 INSUFFICIENT_BALANCE")
    void depositDecreasedBeforeApproval() {
        wallet.deposit(18_000);
        Charge charge = topUpCharge(12_000);
        wallet.withdrawal(1);

        assertThatThrownBy(() -> useCase.confirm(HOLDER_ID, charge.getPgOrderId(), PAYMENT_KEY, 12_000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(INSUFFICIENT_BALANCE);

        verify(pgApproval, never()).approve(any(), anyString());
    }

    @Test
    @DisplayName("결제 계획이 바뀐 뒤 예전 결제 건으로 승인하면 토스 승인 전에 CHARGE_SUPERSEDED")
    void supersededChargeIsRejected() {
        Charge old = directCharge(0);                                   // 카드 30,000
        wallet.deposit(18_000);
        topUpCharge(12_000);                                            // 예치금 결제로 변경

        assertThatThrownBy(() -> useCase.confirm(HOLDER_ID, old.getPgOrderId(), PAYMENT_KEY, 30_000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_SUPERSEDED);

        verify(pgApproval, never()).approve(any(), anyString());
    }

    @Test
    @DisplayName("수동 충전 건을 주문 결제 승인으로 보내면 CHARGE_PAYMENT_MISMATCH")
    void manualChargeIsRejected() {
        Charge manual = register(Charge.requestManual(wallet, HOLDER_ID, 30_000));

        assertThatThrownBy(() -> useCase.confirm(HOLDER_ID, manual.getPgOrderId(), PAYMENT_KEY, 30_000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_PAYMENT_MISMATCH);
    }

    @Test
    @DisplayName("이미 결제가 끝났으면 토스를 부르지 않고 결과만 돌려준다 (멱등)")
    void alreadyPaidIsIdempotent() {
        Charge charge = directCharge(0);
        tossApproves(charge, "카드");
        useCase.confirm(HOLDER_ID, charge.getPgOrderId(), PAYMENT_KEY, 30_000);

        PaymentSummaryDto again = useCase.confirm(HOLDER_ID, charge.getPgOrderId(), PAYMENT_KEY, 30_000);

        assertThat(again.status()).isEqualTo(PaymentStatus.PAID);
        verify(pgApproval).approve(charge, PAYMENT_KEY);                // 한 번만
    }
}
