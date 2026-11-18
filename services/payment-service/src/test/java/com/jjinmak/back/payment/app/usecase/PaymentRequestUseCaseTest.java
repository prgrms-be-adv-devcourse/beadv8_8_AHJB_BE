package com.jjinmak.back.payment.app.usecase;

import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.back.payment.app.dto.HoldDto;
import com.jjinmak.back.payment.app.support.HoldingCreator;
import com.jjinmak.back.payment.app.support.WalletSupport;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.ChargePolicy;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentPlanner;
import com.jjinmak.back.payment.domain.PaymentShare;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.ChargePurpose;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.PgMethod;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_LIMIT_EXCEEDED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_METHOD_NOT_SUPPORTED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_TRANSFER_LIMIT_EXCEEDED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.POINT_USE_OUT_OF_RANGE;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_POINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 주문 결제 요청: 결제수단 · 포인트 사용액에 따른 금액 구성과 부족분 충전 금액. 최소 충전 금액 10,000 / 1회 한도 2,000,000 기준.
 */
class PaymentRequestUseCaseTest {

    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long ORDER_GROUP_ID = 100L;

    private PaymentRequestUseCase useCase;
    private HoldingCreator holdingCreator;
    private ChargeRepository chargeRepository;

    private Wallet buyerWallet;
    private final List<Payment> savedPayments = new ArrayList<>();
    private final List<Charge> savedCharges = new ArrayList<>();

    @BeforeEach
    void setUp() {
        buyerWallet = new Wallet(null, WalletType.USER);

        WalletSupport walletSupport = mock(WalletSupport.class);
        when(walletSupport.getWalletByHolderId(BUYER_ID)).thenReturn(buyerWallet);

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.findByOrderGroupId(ORDER_GROUP_ID))
                .thenAnswer(invocation -> savedPayments.stream().findFirst());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            savedPayments.add(payment);
            return payment;
        });

        chargeRepository = mock(ChargeRepository.class);
        when(chargeRepository.findByPaymentOrderByIdDesc(any()))
                .thenAnswer(invocation -> {
                    List<Charge> latestFirst = new ArrayList<>(savedCharges);
                    java.util.Collections.reverse(latestFirst);
                    return latestFirst;
                });
        when(chargeRepository.save(any(Charge.class))).thenAnswer(invocation -> {
            Charge charge = invocation.getArgument(0);
            savedCharges.add(charge);
            return charge;
        });

        holdingCreator = mock(HoldingCreator.class);

        useCase = new PaymentRequestUseCase(walletSupport, paymentRepository, chargeRepository, holdingCreator,
                new PaymentPlanner(new ChargePolicy(10_000, 2_000_000)), mock(EventPublisher.class));
    }

    // 주문 1건짜리 결제 요청 (결제수단을 보내지 않는 기존 형태 = 전액 예치금 결제)
    private static PaymentRequestDto orderOf(long totalPrice) {
        return new PaymentRequestDto(ORDER_GROUP_ID, BUYER_ID, totalPrice,
                List.of(new PaymentRequestDto.Item(1_001L, SELLER_ID, totalPrice, 0)));
    }

    // 결제수단과 포인트 사용액을 고른 결제 요청
    private static PaymentRequestDto orderOf(long totalPrice, String payMethod, long pointUseAmount) {
        return new PaymentRequestDto(ORDER_GROUP_ID, BUYER_ID, totalPrice,
                List.of(new PaymentRequestDto.Item(1_001L, SELLER_ID, totalPrice, 0)), payMethod, pointUseAmount);
    }

    private void assertErrorCode(PaymentRequestDto request, Object errorCode) {
        assertThatThrownBy(() -> useCase.request(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    @Test
    @DisplayName("예치금이 충분하면 충전 없이 바로 결제된다")
    void enoughBalancePaysWithoutCharge() {
        buyerWallet.deposit(50_000);

        PaymentResultDto result = useCase.request(orderOf(30_000));

        assertThat(result.isPaid()).isTrue();
        assertThat(result.chargeAmount()).isZero();
        assertThat(result.pgMethod()).isNull();
        verify(holdingCreator).hold(any(HoldDto.class));
        verify(chargeRepository, never()).save(any(Charge.class));
    }

    @Test
    @DisplayName("부족분이 최소 충전 금액 이상이면 부족분만 계좌이체로 충전하게 한다")
    void shortageOverMinimum() {
        buyerWallet.deposit(7_000);

        PaymentResultDto result = useCase.request(orderOf(30_000));

        assertThat(result.status()).isEqualTo(PaymentResultDto.Status.NEEDS_CHARGE);
        assertThat(result.shortageAmount()).isEqualTo(23_000);
        assertThat(result.chargeAmount()).isEqualTo(23_000);
        assertThat(result.pgMethod()).isEqualTo("TRANSFER");

        assertThat(savedCharges).hasSize(1);
        assertThat(savedCharges.get(0).getAmount()).isEqualTo(23_000);
        assertThat(result.pgOrderId()).isEqualTo(savedCharges.get(0).getPgOrderId());
        assertThat(savedPayments.get(0).getStatus()).isEqualTo(PaymentStatus.CHARGING);
        assertThat(savedPayments.get(0).getChargeAmount()).isEqualTo(23_000);
        verify(holdingCreator, never()).hold(any(HoldDto.class));
    }

    @Test
    @DisplayName("부족분이 최소 충전 금액보다 작으면 최소 충전 금액으로 올려 충전하게 하고, 실제 부족분도 함께 알려 준다")
    void smallShortageIsRaisedToMinimum() {
        buyerWallet.deposit(29_700);

        PaymentResultDto result = useCase.request(orderOf(30_000));

        assertThat(result.status()).isEqualTo(PaymentResultDto.Status.NEEDS_CHARGE);
        assertThat(result.shortageAmount()).isEqualTo(300);
        assertThat(result.chargeAmount()).isEqualTo(10_000);             // 결제 후 9,700 이 예치금에 남는다
        assertThat(savedCharges.get(0).getAmount()).isEqualTo(10_000);
        assertThat(savedPayments.get(0).getChargeAmount()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("올려 충전한 금액이 주문 총액보다 커도 예치금으로 낸 금액은 주문 총액 그대로다")
    void depositAmountIsTotalEvenWhenChargeIsRaised() {
        buyerWallet.deposit(4_800);

        PaymentResultDto result = useCase.request(orderOf(5_000));

        assertThat(result.shortageAmount()).isEqualTo(200);
        assertThat(result.chargeAmount()).isEqualTo(10_000);
        assertThat(savedPayments.get(0).getDepositAmount()).isEqualTo(5_000);
        assertThat(result.depositAmount()).isEqualTo(5_000);
        assertThat(result.payMethod()).isEqualTo("DEPOSIT");
    }

    // ===== 결제수단 선택 · 포인트 =====

    @Test
    @DisplayName("결제수단을 보내지 않으면 전액 예치금 결제로 기록된다")
    void noPayMethodMeansDeposit() {
        buyerWallet.deposit(50_000);

        PaymentResultDto result = useCase.request(orderOf(30_000));

        Payment payment = savedPayments.get(0);
        assertThat(payment.getPayMethod()).isEqualTo(PayMethod.DEPOSIT);
        assertThat(payment.getShare()).isEqualTo(new PaymentShare(0, 30_000, 0));
        assertThat(result.payMethod()).isEqualTo("DEPOSIT");
        verify(holdingCreator).hold(argThat((HoldDto dto) -> dto.share().equals(new PaymentShare(0, 30_000, 0))));
    }

    @Test
    @DisplayName("포인트 + 예치금: 포인트를 먼저 쓰고 남은 금액을 예치금으로 내며, 예치금이 충분하면 결제창 없이 끝난다")
    void pointAndDeposit() {
        buyerWallet.deposit(25_000);
        buyerWallet.depositPoint(8_000);

        PaymentResultDto result = useCase.request(orderOf(30_000, "DEPOSIT", 8_000));

        assertThat(result.isPaid()).isTrue();
        assertThat(result.pointAmount()).isEqualTo(8_000);
        assertThat(result.depositAmount()).isEqualTo(22_000);
        assertThat(result.pgAmount()).isZero();
        verify(holdingCreator).hold(argThat((HoldDto dto) -> dto.share().equals(new PaymentShare(8_000, 22_000, 0))));
    }

    @Test
    @DisplayName("포인트 + 예치금: 포인트를 뺀 금액에서 예치금이 모자란 만큼만 부족분으로 계산한다")
    void pointAndDepositWithShortage() {
        buyerWallet.deposit(5_000);
        buyerWallet.depositPoint(3_000);

        PaymentResultDto result = useCase.request(orderOf(30_000, "DEPOSIT", 3_000));

        assertThat(result.status()).isEqualTo(PaymentResultDto.Status.NEEDS_CHARGE);
        assertThat(result.shortageAmount()).isEqualTo(22_000);           // 30,000 − 3,000 − 5,000
        assertThat(result.chargeAmount()).isEqualTo(22_000);
        assertThat(result.pgMethod()).isEqualTo("TRANSFER");
        assertThat(savedCharges.get(0).getPurpose()).isEqualTo(ChargePurpose.FOR_PAYMENT);
        assertThat(savedPayments.get(0).getShare()).isEqualTo(new PaymentShare(3_000, 27_000, 0));
    }

    @Test
    @DisplayName("포인트로 전액을 내면 고른 수단과 관계없이 결제창 없이 끝난다")
    void pointOnly() {
        buyerWallet.depositPoint(30_000);

        PaymentResultDto result = useCase.request(orderOf(30_000, "CARD", 30_000));

        assertThat(result.isPaid()).isTrue();
        assertThat(result.pointAmount()).isEqualTo(30_000);
        assertThat(result.pgAmount()).isZero();
        verify(chargeRepository, never()).save(any(Charge.class));
        verify(holdingCreator).hold(argThat((HoldDto dto) -> dto.share().equals(new PaymentShare(30_000, 0, 0))));
    }

    @Test
    @DisplayName("계좌이체 직접 결제: 예치금이 있어도 쓰지 않고 전액을 결제창에서 결제하게 한다")
    void directTransfer() {
        buyerWallet.deposit(50_000);

        PaymentResultDto result = useCase.request(orderOf(30_000, "TRANSFER", 0));

        assertThat(result.status()).isEqualTo(PaymentResultDto.Status.NEEDS_PAYMENT);
        assertThat(result.chargeAmount()).isEqualTo(30_000);
        assertThat(result.pgAmount()).isEqualTo(30_000);
        assertThat(result.depositAmount()).isZero();
        assertThat(result.pgMethod()).isEqualTo("TRANSFER");
        assertThat(result.payMethod()).isEqualTo("TRANSFER");

        Charge charge = savedCharges.get(0);
        assertThat(charge.getPurpose()).isEqualTo(ChargePurpose.DIRECT_PAYMENT);
        assertThat(charge.getAmount()).isEqualTo(30_000);
        assertThat(charge.getRequiredMethod()).isEqualTo(PgMethod.TRANSFER);
        assertThat(savedPayments.get(0).getStatus()).isEqualTo(PaymentStatus.CHARGING);
        verify(holdingCreator, never()).hold(any(HoldDto.class));
    }

    @Test
    @DisplayName("포인트 + 카드 직접 결제: 포인트를 뺀 금액만 카드 결제창으로 결제하게 한다. 최소 충전 금액은 적용하지 않는다")
    void pointAndDirectCard() {
        buyerWallet.depositPoint(27_000);

        PaymentResultDto result = useCase.request(orderOf(30_000, "card", 27_000));

        assertThat(result.status()).isEqualTo(PaymentResultDto.Status.NEEDS_PAYMENT);
        assertThat(result.chargeAmount()).isEqualTo(3_000);              // 10,000 으로 올리지 않는다
        assertThat(result.shortageAmount()).isEqualTo(3_000);
        assertThat(result.pointAmount()).isEqualTo(27_000);
        assertThat(result.pgMethod()).isEqualTo("CARD");
        assertThat(savedCharges.get(0).getRequiredMethod()).isEqualTo(PgMethod.CARD);
        assertThat(savedPayments.get(0).getShare()).isEqualTo(new PaymentShare(27_000, 0, 3_000));
    }

    @Test
    @DisplayName("계좌이체 직접 결제 금액이 1회 한도를 넘으면 PAYMENT_TRANSFER_LIMIT_EXCEEDED, 카드는 한도가 없다")
    void directTransferOverLimit() {
        assertErrorCode(orderOf(2_500_000, "TRANSFER", 0), PAYMENT_TRANSFER_LIMIT_EXCEEDED);
        verify(chargeRepository, never()).save(any(Charge.class));

        PaymentResultDto card = useCase.request(orderOf(2_500_000, "CARD", 0));
        assertThat(card.status()).isEqualTo(PaymentResultDto.Status.NEEDS_PAYMENT);
        assertThat(card.chargeAmount()).isEqualTo(2_500_000);
    }

    @Test
    @DisplayName("포인트 사용액이 음수이거나 결제 금액을 넘으면 POINT_USE_OUT_OF_RANGE")
    void pointUseOutOfRange() {
        buyerWallet.depositPoint(100_000);

        assertErrorCode(orderOf(30_000, "DEPOSIT", -1), POINT_USE_OUT_OF_RANGE);
        assertErrorCode(orderOf(30_000, "DEPOSIT", 30_001), POINT_USE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("포인트 사용액이 포인트 잔액보다 많으면 INSUFFICIENT_POINT")
    void insufficientPoint() {
        buyerWallet.deposit(50_000);
        buyerWallet.depositPoint(1_000);

        assertErrorCode(orderOf(30_000, "DEPOSIT", 1_001), INSUFFICIENT_POINT);
        verify(holdingCreator, never()).hold(any(HoldDto.class));
    }

    @Test
    @DisplayName("지원하지 않는 결제수단이면 PAYMENT_METHOD_NOT_SUPPORTED")
    void unsupportedPayMethod() {
        assertErrorCode(orderOf(30_000, "VIRTUAL_ACCOUNT", 0), PAYMENT_METHOD_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("결제창을 닫고 결제수단을 바꿔 다시 요청하면 새 결제 건을 만들고, 원래 수단으로 돌아오면 그 수단의 건을 다시 쓴다")
    void changingMethodCreatesNewCharge() {
        PaymentResultDto transfer = useCase.request(orderOf(30_000, "TRANSFER", 0));

        PaymentResultDto card = useCase.request(orderOf(30_000, "CARD", 0));
        assertThat(card.pgOrderId()).isNotEqualTo(transfer.pgOrderId());
        assertThat(savedCharges).hasSize(2);
        assertThat(savedCharges.get(0).matchesPaymentPlan()).isFalse();  // 예전 결제창은 승인 단계에서 거절된다
        assertThat(savedCharges.get(1).matchesPaymentPlan()).isTrue();

        PaymentResultDto transferAgain = useCase.request(orderOf(30_000, "TRANSFER", 0));
        assertThat(transferAgain.pgOrderId()).isEqualTo(transfer.pgOrderId());
        assertThat(savedCharges).hasSize(2);
    }

    @Test
    @DisplayName("부족분 충전 금액과 직접 결제 금액이 같아도 충전 건을 직접 결제에 다시 쓰지 않는다")
    void chargeIsNotReusedAcrossPurposes() {
        PaymentResultDto charge = useCase.request(orderOf(30_000));                     // 예치금 0 → 30,000 충전
        PaymentResultDto direct = useCase.request(orderOf(30_000, "TRANSFER", 0));     // 직접 결제 30,000

        assertThat(direct.pgOrderId()).isNotEqualTo(charge.pgOrderId());
        assertThat(savedCharges).extracting(Charge::getPurpose)
                .containsExactly(ChargePurpose.FOR_PAYMENT, ChargePurpose.DIRECT_PAYMENT);
    }

    @Test
    @DisplayName("부족분이 1회 충전 한도를 넘으면 충전 건을 만들지 않고 CHARGE_LIMIT_EXCEEDED")
    void shortageOverLimit() {
        buyerWallet.deposit(1_000);

        assertThatThrownBy(() -> useCase.request(orderOf(2_500_000)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_LIMIT_EXCEEDED);

        verify(chargeRepository, never()).save(any(Charge.class));
        verify(holdingCreator, never()).hold(any(HoldDto.class));
    }

    @Test
    @DisplayName("부족분이 1회 충전 한도와 같으면 충전할 수 있다")
    void shortageEqualsLimit() {
        PaymentResultDto result = useCase.request(orderOf(2_000_000));

        assertThat(result.chargeAmount()).isEqualTo(2_000_000);
        assertThat(savedCharges).hasSize(1);
    }

    @Test
    @DisplayName("결제창을 닫고 같은 잔액으로 다시 요청하면 같은 충전 건을 돌려준다")
    void retryReusesCharge() {
        buyerWallet.deposit(29_700);
        PaymentResultDto first = useCase.request(orderOf(30_000));

        PaymentResultDto second = useCase.request(orderOf(30_000));

        assertThat(second.pgOrderId()).isEqualTo(first.pgOrderId());
        assertThat(savedCharges).hasSize(1);
    }

    @Test
    @DisplayName("다시 요청할 때 잔액이 달라져 충전 금액이 바뀌면 새 충전 건을 만든다")
    void retryWithDifferentBalanceCreatesNewCharge() {
        buyerWallet.deposit(7_000);
        PaymentResultDto first = useCase.request(orderOf(30_000));       // 23,000 충전
        buyerWallet.deposit(20_000);                                     // 그 사이 예치금이 늘었다 → 부족분 3,000

        PaymentResultDto second = useCase.request(orderOf(30_000));

        assertThat(second.shortageAmount()).isEqualTo(3_000);
        assertThat(second.chargeAmount()).isEqualTo(10_000);
        assertThat(second.pgOrderId()).isNotEqualTo(first.pgOrderId());
        assertThat(savedCharges).hasSize(2);
    }
}
