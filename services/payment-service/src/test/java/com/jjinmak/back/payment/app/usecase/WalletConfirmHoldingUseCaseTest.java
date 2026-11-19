package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.app.support.PointEarnCalculator;
import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentPlan;
import com.jjinmak.back.payment.domain.PaymentShare;
import com.jjinmak.back.payment.domain.PointPolicy;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_EARN;
import static com.jjinmak.back.payment.exception.WalletErrorCode.HOLDING_ALREADY_REFUNDED;
import static com.jjinmak.back.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 구매 확정 시 포인트 적립. 적립률 1% 기준.
 * 주문 1건 = 낙찰가 30,000 + 배송비 3,000 = 33,000.
 */
class WalletConfirmHoldingUseCaseTest {

    private static final long ORDER_ID = 1_001L;
    private static final long PAYMENT_ID = 7L;
    private static final long WINNING_PRICE = 30_000L;
    private static final long DELIVERY_FEE = 3_000L;
    private static final long AMOUNT = 33_000L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0);

    private WalletConfirmHoldingUseCase useCase;
    private HoldingRepository holdingRepository;
    private PaymentRepository paymentRepository;

    private Wallet buyer;
    private Wallet seller;
    private final List<WalletTransaction> ledger = new ArrayList<>();

    @BeforeEach
    void setUp() {
        buyer = new Wallet(null, WalletType.USER);
        ReflectionTestUtils.setField(buyer, "id", 1L);
        seller = new Wallet(null, WalletType.USER);
        ReflectionTestUtils.setField(seller, "id", 2L);

        holdingRepository = mock(HoldingRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        WalletTransactionRepository walletTransactionRepository = mock(WalletTransactionRepository.class);
        when(walletTransactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> {
            ledger.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        useCase = new WalletConfirmHoldingUseCase(holdingRepository,
                new PointEarnCalculator(new PointPolicy(1), paymentRepository),
                new LedgerRecorder(walletTransactionRepository));
    }

    // 이 금액 구성으로 낸 홀딩 (포인트 · 예치금 · 직접 결제의 합 = 33,000)
    private Holding holdingPaidWith(PaymentShare share) {
        Holding holding = Holding.hold(ORDER_ID, PAYMENT_ID, buyer, seller, WINNING_PRICE, DELIVERY_FEE, 3_000, share);
        when(holdingRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(holding));
        return holding;
    }

    // 직접 결제가 이 수단으로 승인된 결제 기록
    private void paymentApprovedBy(PayMethod selected, String actualMethod) {
        Payment payment = Payment.create(100L, 1L, buyer, AMOUNT);
        payment.decide(new PaymentPlan(selected, 0L, 0L, AMOUNT, AMOUNT, 0L, false));
        payment.markPaid(NOW, actualMethod);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
    }

    private void assertEarned(Holding holding, long expected) {
        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.CONFIRMED);
        assertThat(holding.getEarnedPoint()).isEqualTo(expected);
        assertThat(buyer.getPointBalance()).isEqualTo(expected);
        if (expected == 0) {
            assertThat(ledger).isEmpty();
            return;
        }
        assertThat(ledger).hasSize(1);
        WalletTransaction record = ledger.get(0);
        assertThat(record.getType()).isEqualTo(POINT_EARN);
        assertThat(record.getAccount()).isEqualTo(WalletAccount.POINT);
        assertThat(record.getAmount()).isEqualTo(expected);
        assertThat(record.getReferenceId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("예치금으로 낸 주문: 배송비를 뺀 낙찰가 몫의 1% 를 적립하고 원장에 POINT_EARN 을 남긴다")
    void depositPaymentEarns() {
        Holding holding = holdingPaidWith(PaymentShare.depositOnly(AMOUNT));

        useCase.confirm(ORDER_ID);

        assertEarned(holding, 300);                                      // 33,000 중 낙찰가 몫 30,000 의 1%
        assertThat(buyer.getBalance()).isZero();                         // 예치금은 건드리지 않는다
    }

    @Test
    @DisplayName("포인트 + 예치금: 포인트로 낸 금액은 빼고 예치금으로 낸 금액에만 적립한다")
    void pointShareIsNotEarned() {
        Holding holding = holdingPaidWith(new PaymentShare(11_000, 22_000, 0));

        useCase.confirm(ORDER_ID);

        assertEarned(holding, 200);                                      // 22,000 × 30/33 = 20,000 의 1%
    }

    @Test
    @DisplayName("계좌이체로 승인된 직접 결제는 적립한다")
    void directTransferEarns() {
        Holding holding = holdingPaidWith(new PaymentShare(0, 0, AMOUNT));
        paymentApprovedBy(PayMethod.TRANSFER, "계좌이체");

        useCase.confirm(ORDER_ID);

        assertEarned(holding, 300);
    }

    @Test
    @DisplayName("카드 · 간편결제로 승인된 직접 결제는 적립하지 않고 원장 기록도 남기지 않는다")
    void directCardDoesNotEarn() {
        Holding holding = holdingPaidWith(new PaymentShare(0, 0, AMOUNT));
        paymentApprovedBy(PayMethod.CARD, "간편결제");

        useCase.confirm(ORDER_ID);

        assertEarned(holding, 0);
    }

    @Test
    @DisplayName("계좌이체를 골랐지만 실제로는 카드로 결제됐으면 적립하지 않는다 (실제 수단 기준)")
    void selectedTransferButPaidByCard() {
        Holding holding = holdingPaidWith(new PaymentShare(0, 0, AMOUNT));
        paymentApprovedBy(PayMethod.TRANSFER, "카드");

        useCase.confirm(ORDER_ID);

        assertEarned(holding, 0);
    }

    @Test
    @DisplayName("카드를 골랐지만 실제로는 계좌이체로 결제됐으면 적립한다 (실제 수단 기준)")
    void selectedCardButPaidByTransfer() {
        Holding holding = holdingPaidWith(new PaymentShare(0, 0, AMOUNT));
        paymentApprovedBy(PayMethod.CARD, "계좌이체");

        useCase.confirm(ORDER_ID);

        assertEarned(holding, 300);
    }

    @Test
    @DisplayName("포인트로 전액을 낸 주문은 적립하지 않는다")
    void pointOnlyDoesNotEarn() {
        Holding holding = holdingPaidWith(new PaymentShare(AMOUNT, 0, 0));

        useCase.confirm(ORDER_ID);

        assertEarned(holding, 0);
    }

    @Test
    @DisplayName("같은 구매 확정을 다시 받아도 한 번만 적립한다 (정산까지 끝난 뒤에도)")
    void confirmIsIdempotent() {
        Holding holding = holdingPaidWith(PaymentShare.depositOnly(AMOUNT));
        useCase.confirm(ORDER_ID);

        useCase.confirm(ORDER_ID);
        assertEarned(holding, 300);

        holding.release();
        useCase.confirm(ORDER_ID);
        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.RELEASED);
        assertThat(buyer.getPointBalance()).isEqualTo(300);
        assertThat(ledger).hasSize(1);
    }

    @Test
    @DisplayName("환불된 주문은 구매 확정할 수 없고 적립도 하지 않는다")
    void refundedHoldingIsRejected() {
        Holding holding = holdingPaidWith(PaymentShare.depositOnly(AMOUNT));
        holding.refund(AMOUNT);

        assertThatThrownBy(() -> useCase.confirm(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(HOLDING_ALREADY_REFUNDED);

        assertThat(buyer.getPointBalance()).isZero();
        assertThat(ledger).isEmpty();
    }

    @Test
    @DisplayName("홀딩이 없으면 HOLDING_NOT_FOUND")
    void holdingNotFound() {
        when(holdingRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.confirm(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(HOLDING_NOT_FOUND);
    }
}
