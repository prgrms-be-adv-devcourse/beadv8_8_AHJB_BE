package com.jjinmak.back.payment.app.facade;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.back.payment.app.dto.ChargeSummaryDto;
import com.jjinmak.back.payment.app.dto.PaymentSummaryDto;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.ReconciliationRun;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.in.scheduler.ReconciliationScheduler;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.ReconciliationRunRepository;
import com.jjinmak.back.payment.out.repository.WalletMemberRepository;
import com.jjinmak.back.payment.out.repository.WalletRepository;
import com.jjinmak.back.payment.out.repository.WalletTransactionRepository;
import com.jjinmak.back.payment.support.PaymentIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.payment.app.support.PgApprovalSupport.METHOD_NOT_ALLOWED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus.MATCHED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationType.LEDGER;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_AMOUNT_OUT_OF_RANGE;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_METHOD_NOT_ALLOWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 예치금 충전을 계좌이체로만 받는 흐름을 실제 PostgreSQL 에서 확인한다: 충전 요청 → 토스 승인(가짜) → 지갑 · 원장 · 충전 건.
 * 토스는 응답만 가짜로 바꾸고, 그 밖의 트랜잭션 · 별도 트랜잭션 기록(ChargeFailureRecorder) · 원장 대사는 운영 코드 그대로 돈다.
 *
 * 테스트마다 테이블을 비우고 시스템 지갑(ESCROW, FEE)만 다시 만든다.
 */
class ChargeTransferOnlyIntegrationTest extends PaymentIntegrationTestSupport {

    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final String PAYMENT_KEY = "pk-test";

    private static final String TABLES = "reconciliation_mismatch, reconciliation_run, wallet_transactions, holding, "
            + "charge, withdrawal, payment_item, payment, toss_webhook_event, wallet, wallet_member";

    @Autowired PaymentFacade paymentFacade;
    @Autowired LedgerRecorder ledgerRecorder;
    @Autowired ReconciliationScheduler reconciliationScheduler;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletTransactionRepository walletTransactionRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired ReconciliationRunRepository runRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager entityManager;

    private Long escrowWalletId;

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
        escrowWalletId = transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("truncate table " + TABLES + " restart identity cascade").executeUpdate();
            walletRepository.save(Wallet.createSystemWallet(WalletType.FEE));
            return walletRepository.save(Wallet.createSystemWallet(WalletType.ESCROW)).getId();
        });
    }

    private void createMemberWithWallet(long memberId) {
        transactionTemplate.executeWithoutResult(status -> {
            WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
            walletRepository.save(Wallet.createUserWallet(member));
        });
    }

    // 구매자가 이미 갖고 있는 예치금. 승인 유스케이스가 토스 승인 뒤에 하는 일과 같다
    private void givenBalance(long amount) {
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = walletRepository.findByHolderId(BUYER_ID).orElseThrow();
            Charge charge = chargeRepository.save(Charge.requestManual(wallet, BUYER_ID, amount));
            ledgerRecorder.deposit(wallet, CHARGE, amount, PAYMENT, charge.getId());
            LocalDateTime now = LocalDateTime.now();
            charge.markCharged("payment-key-" + charge.getId(), now, now, "계좌이체", null);
        });
    }

    // 토스가 이 결제수단으로 승인했다고 답하게 한다
    private void tossApproves(String pgOrderId, long amount, String method) {
        when(tossPaymentsClient.confirm(eq(PAYMENT_KEY), eq(pgOrderId), eq(amount), anyString()))
                .thenReturn(new TossPaymentResponse(PAYMENT_KEY, pgOrderId, "찐막 예치금 충전", "DONE",
                        amount, amount, method, null, "2026-11-18T10:00:00+09:00", null, null, null));
    }

    private static PaymentRequestDto orderOf(long orderGroupId, long orderId, long totalPrice) {
        return new PaymentRequestDto(orderGroupId, BUYER_ID, totalPrice,
                List.of(new PaymentRequestDto.Item(orderId, SELLER_ID, totalPrice, 0)));
    }

    private Wallet buyerWallet() {
        return walletRepository.findByHolderId(BUYER_ID).orElseThrow();
    }

    private Charge chargeOf(String pgOrderId) {
        return chargeRepository.findByPgOrderId(pgOrderId).orElseThrow();
    }

    private ReconciliationRun reconcile() {
        LocalDate today = LocalDate.now();
        reconciliationScheduler.reconcileLedger(today);
        return runRepository.findByTypeAndTargetDate(LEDGER, today).orElseThrow();
    }

    // ===== 수동 충전 =====

    @Test
    @DisplayName("계좌이체로 승인된 수동 충전은 예치금에 반영된다")
    void manualChargeWithTransfer() {
        ChargeSummaryDto requested = paymentFacade.requestCharge(BUYER_ID, 30_000);
        assertThat(requested.pgMethod()).isEqualTo("TRANSFER");
        tossApproves(requested.pgOrderId(), 30_000, "계좌이체");

        ChargeSummaryDto confirmed = paymentFacade.confirmCharge(BUYER_ID, requested.pgOrderId(), PAYMENT_KEY, 30_000);

        assertThat(confirmed.status()).isEqualTo(ChargeStatus.CHARGED);
        assertThat(confirmed.method()).isEqualTo("계좌이체");
        assertThat(buyerWallet().getBalance()).isEqualTo(30_000);
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("카드로 승인된 수동 충전은 토스에서 취소되고 예치금 · 원장에 반영되지 않으며, 충전 건은 사유와 함께 닫힌다")
    void manualChargeWithCardIsRejected() {
        ChargeSummaryDto requested = paymentFacade.requestCharge(BUYER_ID, 30_000);
        tossApproves(requested.pgOrderId(), 30_000, "카드");

        assertThatThrownBy(() -> paymentFacade.confirmCharge(BUYER_ID, requested.pgOrderId(), PAYMENT_KEY, 30_000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_METHOD_NOT_ALLOWED);

        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), anyString());
        assertThat(buyerWallet().getBalance()).isZero();
        assertThat(walletTransactionRepository.count()).isZero();

        // 승인 트랜잭션은 롤백됐지만 닫힌 사유는 별도 트랜잭션으로 남는다
        Charge charge = chargeOf(requested.pgOrderId());
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.ABORTED);
        assertThat(charge.getFailureCode()).isEqualTo(METHOD_NOT_ALLOWED);
        assertThat(charge.getFailureMessage()).contains("카드");
    }

    @Test
    @DisplayName("최소 충전 금액보다 작은 수동 충전 요청은 충전 건을 만들지 않는다")
    void manualChargeBelowMinimum() {
        assertThatThrownBy(() -> paymentFacade.requestCharge(BUYER_ID, 9_999))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_AMOUNT_OUT_OF_RANGE);

        assertThat(chargeRepository.count()).isZero();
    }

    // ===== 주문 결제 중 부족분 충전 =====

    @Test
    @DisplayName("부족분이 최소 충전 금액보다 작으면 최소 충전 금액을 충전받아 결제하고, 차액은 예치금에 남으며 원장 대사도 일치한다")
    void smallShortageIsToppedUpAndRemainderStays() {
        givenBalance(29_700);

        PaymentResultDto requested = paymentFacade.requestPayment(orderOf(100L, 1_001L, 30_000));

        assertThat(requested.status()).isEqualTo(PaymentResultDto.Status.NEEDS_CHARGE);
        assertThat(requested.shortageAmount()).isEqualTo(300);
        assertThat(requested.chargeAmount()).isEqualTo(10_000);
        assertThat(requested.pgMethod()).isEqualTo("TRANSFER");

        tossApproves(requested.pgOrderId(), 10_000, "계좌이체");
        PaymentSummaryDto paid = paymentFacade.confirmPayment(BUYER_ID, requested.pgOrderId(), PAYMENT_KEY, 10_000);

        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.chargeAmount()).isEqualTo(10_000);
        assertThat(buyerWallet().getBalance()).isEqualTo(9_700);                                   // 29,700 + 10,000 − 30,000
        assertThat(walletRepository.findById(escrowWalletId).orElseThrow().getBalance()).isEqualTo(30_000);
        assertThat(chargeOf(requested.pgOrderId()).getStatus()).isEqualTo(ChargeStatus.USED);

        ReconciliationRun run = reconcile();
        assertThat(run.getStatus()).as("failureReason=%s", run.getFailureReason()).isEqualTo(MATCHED);
        assertThat(run.getMismatchCount()).isZero();
    }

    @Test
    @DisplayName("부족분 충전이 카드로 승인되면 취소되고 결제는 끝나지 않는다. 다시 요청하면 새 충전 건으로 이어 갈 수 있다")
    void shortageChargeWithCardIsRejectedAndRetryable() {
        givenBalance(7_000);
        PaymentResultDto requested = paymentFacade.requestPayment(orderOf(100L, 1_001L, 30_000));
        assertThat(requested.chargeAmount()).isEqualTo(23_000);
        tossApproves(requested.pgOrderId(), 23_000, "간편결제");

        assertThatThrownBy(() -> paymentFacade.confirmPayment(BUYER_ID, requested.pgOrderId(), PAYMENT_KEY, 23_000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_METHOD_NOT_ALLOWED);

        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), anyString());
        assertThat(buyerWallet().getBalance()).isEqualTo(7_000);                                   // 충전도 결제도 되지 않았다
        assertThat(walletRepository.findById(escrowWalletId).orElseThrow().getBalance()).isZero();
        assertThat(chargeOf(requested.pgOrderId()).getStatus()).isEqualTo(ChargeStatus.ABORTED);

        PaymentResultDto retried = paymentFacade.requestPayment(orderOf(100L, 1_001L, 30_000));

        assertThat(retried.status()).isEqualTo(PaymentResultDto.Status.NEEDS_CHARGE);
        assertThat(retried.pgOrderId()).isNotEqualTo(requested.pgOrderId());
        assertThat(retried.chargeAmount()).isEqualTo(23_000);
    }

    // ===== 복구 =====

    @Test
    @DisplayName("승인 단계를 끝내지 못한 수동 충전을 복구할 때도 카드 결제는 예치금에 넣지 않고 취소한다")
    void recoveryRejectsCard() {
        ChargeSummaryDto requested = paymentFacade.requestCharge(BUYER_ID, 30_000);
        when(tossPaymentsClient.findByOrderId(requested.pgOrderId()))
                .thenReturn(new TossPaymentResponse(PAYMENT_KEY, requested.pgOrderId(), "찐막 예치금 충전", "DONE",
                        30_000, 30_000, "카드", null, "2026-11-18T10:00:00+09:00", null, null, null));

        paymentFacade.recoverStaleCharge(requested.chargeId());

        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), anyString());
        assertThat(buyerWallet().getBalance()).isZero();
        Charge charge = chargeOf(requested.pgOrderId());
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.ABORTED);
        assertThat(charge.getFailureCode()).isEqualTo(METHOD_NOT_ALLOWED);
    }
}
