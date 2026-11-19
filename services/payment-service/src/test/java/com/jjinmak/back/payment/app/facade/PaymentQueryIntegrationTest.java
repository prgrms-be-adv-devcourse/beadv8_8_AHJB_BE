package com.jjinmak.back.payment.app.facade;

import com.jjinmak.back.payment.app.dto.OrderPaymentDto;
import com.jjinmak.back.payment.app.dto.PaymentDetailDto;
import com.jjinmak.back.payment.app.dto.PaymentPolicyDto;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.WalletMemberRepository;
import com.jjinmak.back.payment.out.repository.WalletRepository;
import com.jjinmak.back.payment.support.PaymentIntegrationTestSupport;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_EARN;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_FORBIDDEN;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND;
import static com.jjinmak.back.payment.exception.WalletErrorCode.HOLDING_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 프론트 연동용 조회(주문별 결제 정보 · 주문 그룹 기준 결제 상세 · 결제 정책)를 실제 PostgreSQL 에서 확인한다.
 * 결제 → 조회 → 구매 확정 · 환불 → 다시 조회. 적립률은 설정값(1%)이다.
 *
 * 테스트마다 테이블을 비우고 시스템 지갑(ESCROW, FEE)만 다시 만든다.
 *
 * 주문 묶음: 주문 1001 = 낙찰 30,000 + 배송 3,000 = 33,000 / 주문 1002 = 낙찰 17,000 → 총액 50,000
 */
class PaymentQueryIntegrationTest extends PaymentIntegrationTestSupport {

    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long ORDER_GROUP_ID = 500L;
    private static final long ORDER_A = 1_001L;      // 낙찰 30,000 + 배송 3,000
    private static final long ORDER_B = 1_002L;      // 낙찰 17,000
    private static final long TOTAL = 50_000L;
    private static final String PAYMENT_KEY = "pk-query";

    private static final String TABLES = "reconciliation_mismatch, reconciliation_run, wallet_transactions, holding, "
            + "charge, withdrawal, payment_item, payment, toss_webhook_event, wallet, wallet_member";

    @Autowired PaymentFacade paymentFacade;
    @Autowired WalletFacade walletFacade;
    @Autowired LedgerRecorder ledgerRecorder;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager entityManager;

    @BeforeEach
    void setUp() {
        resetDatabase();
        createMemberWithWallet(BUYER_ID);
        createMemberWithWallet(SELLER_ID);
    }

    @AfterEach
    void tearDown() {
        resetDatabase();     // 같은 DB 를 쓰는 다른 테스트가 앱을 막 띄운 상태에서 시작하게 한다
    }

    private void resetDatabase() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("truncate table " + TABLES + " restart identity cascade").executeUpdate();
            walletRepository.save(Wallet.createSystemWallet(WalletType.FEE));
            walletRepository.save(Wallet.createSystemWallet(WalletType.ESCROW));
        });
    }

    private void createMemberWithWallet(long memberId) {
        transactionTemplate.executeWithoutResult(status -> {
            WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
            walletRepository.save(Wallet.createUserWallet(member));
        });
    }

    // ===== 데이터 만들기 =====

    // 구매자가 이미 갖고 있는 예치금 (계좌이체 충전)
    private void givenBalance(long amount) {
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = walletRepository.findByHolderId(BUYER_ID).orElseThrow();
            Charge charge = chargeRepository.save(Charge.requestManual(wallet, BUYER_ID, amount));
            ledgerRecorder.deposit(wallet, CHARGE, amount, PAYMENT, charge.getId());
            LocalDateTime now = LocalDateTime.now();
            charge.markCharged("payment-key-" + charge.getId(), now, now, "계좌이체", null);
        });
    }

    // 구매자가 이미 갖고 있는 포인트 (예전 주문 9001 의 구매 확정으로 적립받은 것으로 기록한다)
    private void givenPoint(long amount) {
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = walletRepository.findByHolderId(BUYER_ID).orElseThrow();
            ledgerRecorder.depositPoint(wallet, POINT_EARN, amount, ORDER, 9_001L);
        });
    }

    private static PaymentRequestDto twoOrders(String payMethod, long pointUseAmount) {
        return new PaymentRequestDto(ORDER_GROUP_ID, BUYER_ID, TOTAL,
                List.of(new PaymentRequestDto.Item(ORDER_A, SELLER_ID, 30_000, 3_000),
                        new PaymentRequestDto.Item(ORDER_B, SELLER_ID, 17_000, 0)),
                payMethod, pointUseAmount);
    }

    // 주문 1001 · 1002 를 이 수단과 포인트 사용액으로 결제한다. 직접 결제면 토스가 actualMethod 로 승인했다고 답하게 한다
    private PaymentResultDto pay(String payMethod, long pointUseAmount, String actualMethod) {
        PaymentResultDto requested = paymentFacade.requestPayment(twoOrders(payMethod, pointUseAmount));
        if (requested.isPaid()) return requested;

        long amount = requested.chargeAmount();
        when(tossPaymentsClient.confirm(eq(PAYMENT_KEY), eq(requested.pgOrderId()), eq(amount), anyString()))
                .thenReturn(new TossPaymentResponse(PAYMENT_KEY, requested.pgOrderId(), "찐막 주문 결제", "DONE",
                        amount, amount, actualMethod, null, "2026-11-19T10:00:00+09:00", null, null, null));
        paymentFacade.confirmPayment(BUYER_ID, requested.pgOrderId(), PAYMENT_KEY, amount);
        return requested;
    }

    private static void assertErrorCode(Runnable call, Object errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    // ===== 주문 1건의 결제 정보 =====

    @Test
    @DisplayName("주문별 결제 정보: 예치금 결제 후 구매 확정 전에는 예상 적립 포인트가, 확정 뒤에는 실제 적립 포인트가 보이고 둘은 같은 값이다")
    void orderPaymentBeforeAndAfterConfirm() {
        givenBalance(60_000);
        pay("DEPOSIT", 0, null);

        OrderPaymentDto before = paymentFacade.getOrderPayment(BUYER_ID, ORDER_A);

        assertThat(before.orderGroupId()).isEqualTo(ORDER_GROUP_ID);
        assertThat(before.orderId()).isEqualTo(ORDER_A);
        assertThat(before.payMethod()).isEqualTo(PayMethod.DEPOSIT);
        assertThat(before.amount()).isEqualTo(33_000);
        assertThat(before.depositAmount()).isEqualTo(33_000);
        assertThat(before.pointAmount()).isZero();
        assertThat(before.pgAmount()).isZero();
        assertThat(before.status()).isEqualTo(HoldingStatus.HELD);
        assertThat(before.heldAt()).isNotNull();
        assertThat(before.expectedEarnPoint()).isEqualTo(300);            // 33,000 중 낙찰가 30,000 의 1%
        assertThat(before.earnedPoint()).isZero();

        walletFacade.confirmHolding(ORDER_A);
        OrderPaymentDto after = paymentFacade.getOrderPayment(BUYER_ID, ORDER_A);

        assertThat(after.status()).isEqualTo(HoldingStatus.CONFIRMED);
        assertThat(after.expectedEarnPoint()).isZero();
        assertThat(after.earnedPoint()).isEqualTo(before.expectedEarnPoint());
        assertThat(after.confirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("주문별 결제 정보: 포인트 + 카드 직접 결제는 주문별 포인트 · 직접 결제 몫과 실제 승인 수단을 돌려주고 예상 적립은 0 이다")
    void orderPaymentOfPointAndCard() {
        givenPoint(5_000);
        pay("CARD", 5_000, "간편결제");

        OrderPaymentDto orderA = paymentFacade.getOrderPayment(BUYER_ID, ORDER_A);
        OrderPaymentDto orderB = paymentFacade.getOrderPayment(BUYER_ID, ORDER_B);

        assertThat(orderA.payMethod()).isEqualTo(PayMethod.CARD);
        assertThat(orderA.pgMethod()).isEqualTo("간편결제");
        assertThat(orderA.pointAmount()).isEqualTo(3_300);                // 5,000 × 33,000 / 50,000
        assertThat(orderA.pgAmount()).isEqualTo(29_700);
        assertThat(orderA.depositAmount()).isZero();
        assertThat(orderB.pointAmount()).isEqualTo(1_700);
        assertThat(orderB.pgAmount()).isEqualTo(15_300);
        assertThat(orderA.expectedEarnPoint()).isZero();
        assertThat(orderB.expectedEarnPoint()).isZero();
        assertThat(orderA.paymentId()).isEqualTo(orderB.paymentId());
    }

    @Test
    @DisplayName("주문별 결제 정보: 환불된 주문은 REFUNDED 로 보이고 돌려준 금액 구성이 남는다")
    void orderPaymentAfterRefund() {
        givenBalance(60_000);
        givenPoint(5_000);
        pay("DEPOSIT", 5_000, null);

        walletFacade.refund(7_001L, ORDER_A, 33_000);
        OrderPaymentDto refunded = paymentFacade.getOrderPayment(BUYER_ID, ORDER_A);

        assertThat(refunded.status()).isEqualTo(HoldingStatus.REFUNDED);
        assertThat(refunded.pointAmount()).isEqualTo(3_300);
        assertThat(refunded.depositAmount()).isEqualTo(29_700);
        assertThat(refunded.expectedEarnPoint()).isZero();
        assertThat(refunded.earnedPoint()).isZero();
        assertThat(refunded.refundedAt()).isNotNull();
    }

    @Test
    @DisplayName("주문별 결제 정보: 판매자는 볼 수 없고, 결제가 끝나지 않은 주문은 홀딩이 없다")
    void orderPaymentIsBuyerOnly() {
        givenBalance(60_000);
        pay("DEPOSIT", 0, null);

        assertErrorCode(() -> paymentFacade.getOrderPayment(SELLER_ID, ORDER_A), PAYMENT_FORBIDDEN);
        assertErrorCode(() -> paymentFacade.getOrderPayment(BUYER_ID, 9_999L), HOLDING_NOT_FOUND);
    }

    // ===== 주문 그룹 기준 결제 상세 =====

    @Test
    @DisplayName("주문 그룹 결제 상세: 결제창 없이 끝난 결제를 orderGroupId 로 찾을 수 있고 paymentId 로 조회한 결과와 같다")
    void paymentByOrderGroupEqualsPaymentById() {
        givenBalance(60_000);
        PaymentResultDto paid = pay("DEPOSIT", 0, null);
        assertThat(paid.isPaid()).isTrue();                               // 결제창을 거치지 않았다

        PaymentDetailDto byGroup = paymentFacade.getPaymentByOrderGroup(BUYER_ID, ORDER_GROUP_ID);
        PaymentDetailDto byId = paymentFacade.getPayment(BUYER_ID, byGroup.paymentId());

        assertThat(byGroup).isEqualTo(byId);
        assertThat(byGroup.orderGroupId()).isEqualTo(ORDER_GROUP_ID);
        assertThat(byGroup.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(byGroup.totalPrice()).isEqualTo(TOTAL);
        assertThat(byGroup.items()).extracting(PaymentDetailDto.Item::orderId).containsExactlyInAnyOrder(ORDER_A, ORDER_B);
    }

    @Test
    @DisplayName("주문 그룹 결제 상세: 예상 적립 포인트는 아직 확정하지 않은 주문의 합이라 구매 확정 · 환불할 때마다 줄어든다")
    void expectedEarnPointShrinksAsOrdersClose() {
        givenBalance(60_000);
        pay("DEPOSIT", 0, null);
        assertThat(paymentFacade.getPaymentByOrderGroup(BUYER_ID, ORDER_GROUP_ID).expectedEarnPoint())
                .isEqualTo(300 + 170);                                    // 30,000 의 1% + 17,000 의 1%

        walletFacade.confirmHolding(ORDER_A);
        assertThat(paymentFacade.getPaymentByOrderGroup(BUYER_ID, ORDER_GROUP_ID).expectedEarnPoint())
                .isEqualTo(170);

        walletFacade.refund(7_001L, ORDER_B, 17_000);
        assertThat(paymentFacade.getPaymentByOrderGroup(BUYER_ID, ORDER_GROUP_ID).expectedEarnPoint())
                .isZero();
    }

    @Test
    @DisplayName("주문 그룹 결제 상세: 결제창을 기다리는 결제는 홀딩이 없어 예상 적립 포인트가 0 이다")
    void expectedEarnPointIsZeroWhileCharging() {
        PaymentResultDto requested = paymentFacade.requestPayment(twoOrders("TRANSFER", 0));
        assertThat(requested.isPaid()).isFalse();

        PaymentDetailDto detail = paymentFacade.getPaymentByOrderGroup(BUYER_ID, ORDER_GROUP_ID);

        assertThat(detail.status()).isEqualTo(PaymentStatus.CHARGING);
        assertThat(detail.expectedEarnPoint()).isZero();
    }

    @Test
    @DisplayName("주문 그룹 결제 상세: 다른 회원의 결제나 없는 주문 그룹은 없는 결제로 취급한다")
    void paymentByOrderGroupOfOthersIsNotFound() {
        givenBalance(60_000);
        pay("DEPOSIT", 0, null);

        assertErrorCode(() -> paymentFacade.getPaymentByOrderGroup(SELLER_ID, ORDER_GROUP_ID), PAYMENT_NOT_FOUND);
        assertErrorCode(() -> paymentFacade.getPaymentByOrderGroup(BUYER_ID, 9_999L), PAYMENT_NOT_FOUND);
    }

    // ===== 결제 정책 =====

    @Test
    @DisplayName("결제 정책: 설정 파일의 충전 금액 범위 · 적립률 · 수수료율을 돌려준다")
    void policyMatchesConfiguration() {
        PaymentPolicyDto policy = paymentFacade.getPolicy();

        assertThat(policy.chargeMinAmount()).isEqualTo(10_000);
        assertThat(policy.chargeMaxAmount()).isEqualTo(2_000_000);
        assertThat(policy.pointEarnRatePercent()).isEqualTo(1);
        assertThat(policy.feeRatePercent()).isEqualTo(10);
    }
}
