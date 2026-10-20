package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Charge;
import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.boundedContext.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.boundedContext.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.boundedContext.payment.out.ChargeRepository;
import com.jjinmak.back.boundedContext.payment.out.PaymentRepository;
import com.jjinmak.back.boundedContext.payment.out.TossPaymentsClient;
import com.jjinmak.back.boundedContext.payment.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletRepository;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.payment.app.PaymentCloseOnOrderDeletedUseCase.ORDER_DELETED_CODE;
import static com.jjinmak.back.boundedContext.payment.app.PaymentCloseOnOrderDeletedUseCase.ORDER_DELETED_REASON;
import static com.jjinmak.back.boundedContext.payment.exception.PaymentErrorCode.INVALID_PAYMENT_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 주문 그룹 삭제 이벤트 → 미완료 결제 종료 (WalletFacade 경유, 리스너와 같은 진입점).
 */
@SpringBootTest
@Transactional
class PaymentCloseOnOrderDeletedTest {

    private static final Long BUYER_ID = 9_501L;
    private static final Long SELLER_ID = 9_502L;

    @Autowired WalletFacade walletFacade;
    @Autowired PaymentFacade paymentFacade;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ChargeRepository chargeRepository;

    @MockitoBean TossPaymentsClient toss;
    @MockitoBean SettlementEventListener settlementEventListener;

    private Wallet buyerWallet;

    @BeforeEach
    void setUp() {
        buyerWallet = walletOf(BUYER_ID);
        walletOf(SELLER_ID);
    }

    private Wallet walletOf(Long memberId) {
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        return walletRepository.save(Wallet.createUserWallet(member));
    }

    /** 잔액 부족으로 CHARGING 인 결제 + REQUESTED 충전 요청 */
    private Payment chargingPayment(Long orderGroupId, long total) {
        Payment payment = Payment.create(orderGroupId, BUYER_ID, buyerWallet, total);
        payment.addItem(orderGroupId * 10, SELLER_ID, total, 0L);
        payment.validateItems();
        payment.decideCharge(buyerWallet.getBalance());
        payment = paymentRepository.save(payment);
        chargeRepository.save(Charge.requestForPayment(buyerWallet, payment));
        return payment;
    }

    @Test
    @DisplayName("충전 중(CHARGING) 결제 → FAILED + 사유, 승인 전 충전 요청 → ABORTED")
    void close_charging() {
        Payment payment = chargingPayment(8_001L, 10_000L);
        Charge charge = chargeRepository.findByPaymentOrderByIdDesc(payment).getFirst();

        walletFacade.closePaymentByOrderGroupDeleted(8_001L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(ORDER_DELETED_REASON);
        assertThat(payment.getFailedAt()).isNotNull();
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.ABORTED);
        assertThat(charge.getFailureCode()).isEqualTo(ORDER_DELETED_CODE);
    }

    @Test
    @DisplayName("요청만 된(PENDING) 결제 → FAILED")
    void close_pending() {
        Payment payment = paymentRepository.save(Payment.create(8_002L, BUYER_ID, buyerWallet, 10_000L));

        walletFacade.closePaymentByOrderGroupDeleted(8_002L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("결제가 없는 그룹(결제 요청 전에 삭제) → 아무 일도 없음")
    void close_noPayment() {
        walletFacade.closePaymentByOrderGroupDeleted(8_003L);

        assertThat(paymentRepository.findByOrderGroupId(8_003L)).isEmpty();
    }

    @Test
    @DisplayName("이미 결제 완료(PAID)된 결제는 건드리지 않는다")
    void close_paid_untouched() {
        buyerWallet.deposit(10_000L);
        Payment payment = Payment.create(8_004L, BUYER_ID, buyerWallet, 10_000L);
        payment.addItem(80_040L, SELLER_ID, 10_000L, 0L);
        payment.validateItems();
        payment.markPaid(LocalDateTime.now());
        paymentRepository.save(payment);

        walletFacade.closePaymentByOrderGroupDeleted(8_004L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getFailedAt()).isNull();
    }

    @Test
    @DisplayName("같은 이벤트를 두 번 받아도 한 번만 처리된다 (멱등)")
    void close_idempotent() {
        Payment payment = chargingPayment(8_005L, 10_000L);

        walletFacade.closePaymentByOrderGroupDeleted(8_005L);
        LocalDateTime firstFailedAt = payment.getFailedAt();
        walletFacade.closePaymentByOrderGroupDeleted(8_005L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isEqualTo(firstFailedAt);
    }

    @Test
    @DisplayName("닫힌 결제의 충전 요청으로 승인하면 토스를 부르지 않고 거절된다 (PAYMENT005)")
    void confirm_afterClose_rejected() {
        Payment payment = chargingPayment(8_006L, 10_000L);
        Charge charge = chargeRepository.findByPaymentOrderByIdDesc(payment).getFirst();

        walletFacade.closePaymentByOrderGroupDeleted(8_006L);

        assertThatThrownBy(() -> paymentFacade.confirmPayment(BUYER_ID, charge.getPgOrderId(), "pk_late", 10_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(INVALID_PAYMENT_STATUS);
        verify(toss, never()).confirm(anyString(), anyString(), anyLong(), anyString());
        assertThat(buyerWallet.getBalance()).isZero();
    }
}
