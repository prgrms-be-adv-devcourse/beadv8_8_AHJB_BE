package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.payment.app.dto.OrderPaymentDto;
import com.jjinmak.back.payment.app.support.PointEarnCalculator;
import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentPlan;
import com.jjinmak.back.payment.domain.PaymentShare;
import com.jjinmak.back.payment.domain.PointPolicy;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_FORBIDDEN;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND;
import static com.jjinmak.back.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 주문 1건의 결제 정보 조회. 적립률 1% 기준.
 * 주문 1건 = 낙찰가 30,000 + 배송비 3,000 = 33,000.
 */
class PaymentGetOrderPaymentUseCaseTest {

    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long ORDER_ID = 1_001L;
    private static final long ORDER_GROUP_ID = 100L;
    private static final long PAYMENT_ID = 7L;
    private static final long WINNING_PRICE = 30_000L;
    private static final long DELIVERY_FEE = 3_000L;
    private static final long AMOUNT = 33_000L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 0);

    private PaymentGetOrderPaymentUseCase useCase;
    private HoldingRepository holdingRepository;
    private PaymentRepository paymentRepository;

    private Wallet buyer;
    private Wallet seller;

    @BeforeEach
    void setUp() {
        buyer = new Wallet(null, WalletType.USER);
        ReflectionTestUtils.setField(buyer, "id", 1L);
        seller = new Wallet(null, WalletType.USER);
        ReflectionTestUtils.setField(seller, "id", 2L);

        holdingRepository = mock(HoldingRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        useCase = new PaymentGetOrderPaymentUseCase(holdingRepository, paymentRepository,
                new PointEarnCalculator(new PointPolicy(1), paymentRepository));
    }

    // 이 금액 구성으로 낸 홀딩 (포인트 · 예치금 · 직접 결제의 합 = 33,000)
    private Holding holdingPaidWith(PaymentShare share) {
        Holding holding = Holding.hold(ORDER_ID, PAYMENT_ID, buyer, seller, WINNING_PRICE, DELIVERY_FEE, 3_000, share);
        when(holdingRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(holding));
        return holding;
    }

    // 이 수단과 금액 구성으로 끝난 결제. 직접 결제면 토스가 actualMethod 로 승인했다
    private void paymentPaidWith(PayMethod selected, PaymentShare share, String actualMethod) {
        Payment payment = Payment.create(ORDER_GROUP_ID, BUYER_ID, buyer, AMOUNT);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.decide(new PaymentPlan(selected, share.pointAmount(), share.depositAmount(), share.pgAmount(),
                share.pgAmount(), 0L, false));
        payment.markPaid(NOW, actualMethod);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
    }

    @Test
    @DisplayName("포인트 + 예치금으로 낸 주문: 결제 구성과 결제 식별자를 돌려주고, 예치금으로 낸 금액에만 예상 적립 포인트를 계산한다")
    void pointAndDeposit() {
        PaymentShare share = new PaymentShare(11_000, 22_000, 0);
        holdingPaidWith(share);
        paymentPaidWith(PayMethod.DEPOSIT, share, null);

        OrderPaymentDto result = useCase.get(BUYER_ID, ORDER_ID);

        assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.orderGroupId()).isEqualTo(ORDER_GROUP_ID);
        assertThat(result.orderId()).isEqualTo(ORDER_ID);
        assertThat(result.payMethod()).isEqualTo(PayMethod.DEPOSIT);
        assertThat(result.pgMethod()).isNull();
        assertThat(result.winningPrice()).isEqualTo(WINNING_PRICE);
        assertThat(result.deliveryFee()).isEqualTo(DELIVERY_FEE);
        assertThat(result.amount()).isEqualTo(AMOUNT);
        assertThat(result.pointAmount()).isEqualTo(11_000);
        assertThat(result.depositAmount()).isEqualTo(22_000);
        assertThat(result.pgAmount()).isZero();
        assertThat(result.pointAmount() + result.depositAmount() + result.pgAmount()).isEqualTo(result.amount());
        assertThat(result.expectedEarnPoint()).isEqualTo(200);            // 22,000 × 30/33 = 20,000 의 1%
        assertThat(result.earnedPoint()).isZero();
        assertThat(result.status()).isEqualTo(HoldingStatus.HELD);
        assertThat(result.confirmedAt()).isNull();
        assertThat(result.refundedAt()).isNull();
    }

    @Test
    @DisplayName("계좌이체로 승인된 직접 결제는 예상 적립 포인트가 있다")
    void directTransferExpectsEarn() {
        PaymentShare share = new PaymentShare(0, 0, AMOUNT);
        holdingPaidWith(share);
        paymentPaidWith(PayMethod.TRANSFER, share, "계좌이체");

        OrderPaymentDto result = useCase.get(BUYER_ID, ORDER_ID);

        assertThat(result.payMethod()).isEqualTo(PayMethod.TRANSFER);
        assertThat(result.pgMethod()).isEqualTo("계좌이체");
        assertThat(result.pgAmount()).isEqualTo(AMOUNT);
        assertThat(result.expectedEarnPoint()).isEqualTo(300);            // 33,000 중 낙찰가 30,000 의 1%
    }

    @Test
    @DisplayName("카드 · 간편결제로 승인된 직접 결제는 예상 적립 포인트가 0 이다. 계좌이체를 골랐어도 실제 승인 수단을 따른다")
    void directCardExpectsNoEarn() {
        PaymentShare share = new PaymentShare(0, 0, AMOUNT);
        holdingPaidWith(share);
        paymentPaidWith(PayMethod.TRANSFER, share, "간편결제");

        OrderPaymentDto result = useCase.get(BUYER_ID, ORDER_ID);

        assertThat(result.pgMethod()).isEqualTo("간편결제");
        assertThat(result.expectedEarnPoint()).isZero();
    }

    @Test
    @DisplayName("구매 확정된 주문: 예상 적립 포인트는 0 이 되고 실제 적립된 포인트를 돌려준다")
    void confirmedShowsEarnedPoint() {
        PaymentShare share = PaymentShare.depositOnly(AMOUNT);
        Holding holding = holdingPaidWith(share);
        paymentPaidWith(PayMethod.DEPOSIT, share, null);
        holding.confirm(300);

        OrderPaymentDto result = useCase.get(BUYER_ID, ORDER_ID);

        assertThat(result.status()).isEqualTo(HoldingStatus.CONFIRMED);
        assertThat(result.expectedEarnPoint()).isZero();
        assertThat(result.earnedPoint()).isEqualTo(300);
        assertThat(result.confirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("환불된 주문: 예상 · 실제 적립 포인트가 모두 0 이고 돌려준 금액 구성은 그대로 보인다")
    void refundedHasNoEarn() {
        PaymentShare share = new PaymentShare(11_000, 22_000, 0);
        Holding holding = holdingPaidWith(share);
        paymentPaidWith(PayMethod.DEPOSIT, share, null);
        holding.refund(AMOUNT);

        OrderPaymentDto result = useCase.get(BUYER_ID, ORDER_ID);

        assertThat(result.status()).isEqualTo(HoldingStatus.REFUNDED);
        assertThat(result.expectedEarnPoint()).isZero();
        assertThat(result.earnedPoint()).isZero();
        assertThat(result.pointAmount()).isEqualTo(11_000);
        assertThat(result.depositAmount()).isEqualTo(22_000);
        assertThat(result.refundedAt()).isNotNull();
    }

    @Test
    @DisplayName("구매자가 아니면 조회할 수 없다. 그 주문의 판매자도 마찬가지다")
    void onlyBuyerCanRead() {
        PaymentShare share = PaymentShare.depositOnly(AMOUNT);
        holdingPaidWith(share);
        paymentPaidWith(PayMethod.DEPOSIT, share, null);

        assertThatThrownBy(() -> useCase.get(SELLER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_FORBIDDEN);
    }

    @Test
    @DisplayName("아직 결제가 끝나지 않아 홀딩이 없는 주문은 HOLDING_NOT_FOUND")
    void unpaidOrderHasNoHolding() {
        when(holdingRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(BUYER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(HOLDING_NOT_FOUND);
    }

    @Test
    @DisplayName("홀딩은 있는데 결제 기록이 없으면 PAYMENT_NOT_FOUND")
    void missingPayment() {
        holdingPaidWith(PaymentShare.depositOnly(AMOUNT));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(BUYER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_NOT_FOUND);
    }
}
