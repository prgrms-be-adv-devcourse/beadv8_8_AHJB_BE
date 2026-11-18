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
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_LIMIT_EXCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 주문 결제 요청의 부족분 충전 금액. 최소 충전 금액 10,000 / 1회 한도 2,000,000 기준.
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
        when(chargeRepository.findFirstByPaymentAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenAnswer(invocation -> savedCharges.isEmpty()
                        ? Optional.empty() : Optional.of(savedCharges.get(savedCharges.size() - 1)));
        when(chargeRepository.save(any(Charge.class))).thenAnswer(invocation -> {
            Charge charge = invocation.getArgument(0);
            savedCharges.add(charge);
            return charge;
        });

        holdingCreator = mock(HoldingCreator.class);

        useCase = new PaymentRequestUseCase(walletSupport, paymentRepository, chargeRepository, holdingCreator,
                new ChargePolicy(10_000, 2_000_000), mock(EventPublisher.class));
    }

    // 주문 1건짜리 결제 요청
    private static PaymentRequestDto orderOf(long totalPrice) {
        return new PaymentRequestDto(ORDER_GROUP_ID, BUYER_ID, totalPrice,
                List.of(new PaymentRequestDto.Item(1_001L, SELLER_ID, totalPrice, 0)));
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
    @DisplayName("올려 충전한 금액이 주문 총액보다 커도 결제 내역의 기존 예치금 사용액은 음수가 되지 않는다")
    void walletUsedAmountIsNeverNegative() {
        buyerWallet.deposit(4_800);

        PaymentResultDto result = useCase.request(orderOf(5_000));

        assertThat(result.shortageAmount()).isEqualTo(200);
        assertThat(result.chargeAmount()).isEqualTo(10_000);
        assertThat(savedPayments.get(0).getWalletUsedAmount()).isZero();
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
