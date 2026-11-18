package com.jjinmak.back.payment.app.facade;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.back.payment.app.dto.PaymentSummaryDto;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentShare;
import com.jjinmak.back.payment.domain.ReconciliationMismatch;
import com.jjinmak.back.payment.domain.ReconciliationRun;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.enums.ChargePurpose;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.payment.domain.enums.PayMethod;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.exception.PgApiException;
import com.jjinmak.back.payment.in.scheduler.ReconciliationScheduler;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.back.payment.out.repository.ReconciliationMismatchRepository;
import com.jjinmak.back.payment.out.repository.ReconciliationRunRepository;
import com.jjinmak.back.payment.out.repository.WalletMemberRepository;
import com.jjinmak.back.payment.out.repository.WalletRepository;
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

import static com.jjinmak.back.payment.domain.enums.MismatchType.BALANCE_LEDGER_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.CHARGE_LEDGER_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.HOLDING_LEDGER_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus.MATCHED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus.MISMATCHED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationType.LEDGER;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_EARN;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.CHARGE_SUPERSEDED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PG_CANCEL_FAILED;
import static com.jjinmak.back.payment.exception.WalletErrorCode.INSUFFICIENT_POINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제수단 선택 · 직접 결제 · 원결제수단 환불을 실제 PostgreSQL 에서 확인한다:
 * 결제 요청 → (토스 승인, 가짜) → 지갑 · 원장 · 홀딩 · 결제 건 → 환불 → 원장 대사.
 * 토스는 응답만 가짜로 바꾸고, 그 밖의 트랜잭션 · 원장 대사는 운영 코드 그대로 돈다.
 *
 * 테스트마다 테이블을 비우고 시스템 지갑(ESCROW, FEE)만 다시 만든다.
 *
 * 주문 묶음 (twoOrders): 주문 1001 = 낙찰 30,000 + 배송 3,000 = 33,000 / 주문 1002 = 낙찰 17,000 → 총액 50,000
 */
class PaymentMethodIntegrationTest extends PaymentIntegrationTestSupport {

    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long ORDER_GROUP_ID = 500L;
    private static final long ORDER_A = 1_001L;      // 33,000
    private static final long ORDER_B = 1_002L;      // 17,000
    private static final long TOTAL = 50_000L;
    private static final String PAYMENT_KEY = "pk-direct";

    private static final String TABLES = "reconciliation_mismatch, reconciliation_run, wallet_transactions, holding, "
            + "charge, withdrawal, payment_item, payment, toss_webhook_event, wallet, wallet_member";

    @Autowired PaymentFacade paymentFacade;
    @Autowired WalletFacade walletFacade;
    @Autowired LedgerRecorder ledgerRecorder;
    @Autowired ReconciliationScheduler reconciliationScheduler;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired HoldingRepository holdingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ReconciliationRunRepository runRepository;
    @Autowired ReconciliationMismatchRepository mismatchRepository;
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

    // 구매자가 이미 갖고 있는 포인트 (적립)
    private void givenPoint(long amount) {
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = walletRepository.findByHolderId(BUYER_ID).orElseThrow();
            ledgerRecorder.depositPoint(wallet, POINT_EARN, amount, ORDER, 9_999L);
        });
    }

    // 결제창에 있는 동안 다른 곳에서 포인트를 썼다
    private void pointSpentElsewhere(long amount) {
        transactionTemplate.executeWithoutResult(status ->
                walletRepository.findByHolderId(BUYER_ID).orElseThrow().usePoint(amount));
    }

    private static PaymentRequestDto twoOrders(String payMethod, long pointUseAmount) {
        return new PaymentRequestDto(ORDER_GROUP_ID, BUYER_ID, TOTAL,
                List.of(new PaymentRequestDto.Item(ORDER_A, SELLER_ID, 30_000, 3_000),
                        new PaymentRequestDto.Item(ORDER_B, SELLER_ID, 17_000, 0)),
                payMethod, pointUseAmount);
    }

    // 토스가 이 결제수단으로 승인했다고 답하게 한다
    private void tossApproves(String pgOrderId, long amount, String method) {
        when(tossPaymentsClient.confirm(eq(PAYMENT_KEY), eq(pgOrderId), eq(amount), anyString()))
                .thenReturn(new TossPaymentResponse(PAYMENT_KEY, pgOrderId, "찐막 주문 결제", "DONE",
                        amount, amount, method, null, "2026-11-18T10:00:00+09:00", null, null, null));
    }

    // 직접 결제를 요청하고 토스 승인까지 끝낸다
    private PaymentSummaryDto payDirectly(String payMethod, long pointUseAmount, String actualMethod) {
        PaymentResultDto requested = paymentFacade.requestPayment(twoOrders(payMethod, pointUseAmount));
        assertThat(requested.status()).isEqualTo(PaymentResultDto.Status.NEEDS_PAYMENT);
        tossApproves(requested.pgOrderId(), requested.chargeAmount(), actualMethod);
        return paymentFacade.confirmPayment(BUYER_ID, requested.pgOrderId(), PAYMENT_KEY, requested.chargeAmount());
    }

    // ===== 결과 읽기 =====

    private Wallet buyerWallet() {
        return walletRepository.findByHolderId(BUYER_ID).orElseThrow();
    }

    private long escrowBalance() {
        return walletRepository.findById(escrowWalletId).orElseThrow().getBalance();
    }

    private Holding holdingOf(long orderId) {
        return holdingRepository.findByOrderId(orderId).orElseThrow();
    }

    private Payment payment() {
        return paymentRepository.findByOrderGroupId(ORDER_GROUP_ID).orElseThrow();
    }

    private Charge directCharge() {
        return chargeRepository.findAll().stream()
                .filter(charge -> charge.getPurpose() == ChargePurpose.DIRECT_PAYMENT)
                .filter(charge -> charge.getStatus() != ChargeStatus.REQUESTED)
                .findFirst().orElseThrow();
    }

    private ReconciliationRun reconcile() {
        LocalDate today = LocalDate.now();
        reconciliationScheduler.reconcileLedger(today);
        return runRepository.findByTypeAndTargetDate(LEDGER, today).orElseThrow();
    }

    private void assertLedgerMatched() {
        ReconciliationRun run = reconcile();
        assertThat(mismatchRepository.findByRun(run))
                .as("원장 대사 불일치")
                .extracting(ReconciliationMismatch::getDetail)
                .isEmpty();
        assertThat(run.getStatus()).isEqualTo(MATCHED);
    }

    // ===== 포인트 + 예치금 =====

    @Test
    @DisplayName("포인트 + 예치금: 결제창 없이 결제되고, 포인트는 주문 금액에 비례해 나뉘며 원장 대사가 일치한다")
    void pointAndDeposit() {
        givenBalance(60_000);
        givenPoint(10_000);

        PaymentResultDto result = paymentFacade.requestPayment(twoOrders("DEPOSIT", 10_000));

        assertThat(result.isPaid()).isTrue();
        assertThat(result.pointAmount()).isEqualTo(10_000);
        assertThat(result.depositAmount()).isEqualTo(40_000);
        assertThat(buyerWallet().getBalance()).isEqualTo(20_000);
        assertThat(buyerWallet().getPointBalance()).isZero();
        assertThat(escrowBalance()).isEqualTo(TOTAL);                    // 포인트로 낸 금액도 ESCROW 에는 예치금으로 쌓인다

        // 포인트 10,000 을 33,000 : 17,000 으로 나눈다 → 6,600 / 3,400
        assertThat(holdingOf(ORDER_A).getShare()).isEqualTo(new PaymentShare(6_600, 26_400, 0));
        assertThat(holdingOf(ORDER_B).getShare()).isEqualTo(new PaymentShare(3_400, 13_600, 0));

        Payment payment = payment();
        assertThat(payment.getPayMethod()).isEqualTo(PayMethod.DEPOSIT);
        assertThat(payment.getShare()).isEqualTo(new PaymentShare(10_000, 40_000, 0));
        assertThat(payment.getPgMethod()).isNull();
        assertLedgerMatched();
    }

    @Test
    @DisplayName("포인트 + 예치금 환불: 주문 1건을 환불하면 그 주문의 포인트 몫은 포인트로, 예치금 몫은 예치금으로 돌아간다")
    void refundPointAndDeposit() {
        givenBalance(60_000);
        givenPoint(10_000);
        paymentFacade.requestPayment(twoOrders("DEPOSIT", 10_000));

        walletFacade.refund(7_001L, ORDER_A, 33_000);

        assertThat(buyerWallet().getPointBalance()).isEqualTo(6_600);
        assertThat(buyerWallet().getBalance()).isEqualTo(20_000 + 26_400);
        assertThat(escrowBalance()).isEqualTo(17_000);                   // 주문 1002 몫만 남는다
        assertThat(holdingOf(ORDER_A).getStatus()).isEqualTo(HoldingStatus.REFUNDED);
        assertThat(holdingOf(ORDER_B).getStatus()).isEqualTo(HoldingStatus.HELD);
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyLong(), anyString());
        assertLedgerMatched();
    }

    @Test
    @DisplayName("포인트 + 예치금인데 예치금이 모자라면 포인트를 뺀 금액의 부족분만 계좌이체로 충전해 결제한다")
    void pointAndDepositWithTopUp() {
        givenBalance(25_000);
        givenPoint(10_000);

        PaymentResultDto requested = paymentFacade.requestPayment(twoOrders("DEPOSIT", 10_000));
        assertThat(requested.status()).isEqualTo(PaymentResultDto.Status.NEEDS_CHARGE);
        assertThat(requested.shortageAmount()).isEqualTo(15_000);        // 50,000 − 10,000 − 25,000
        assertThat(requested.chargeAmount()).isEqualTo(15_000);
        assertThat(requested.pgMethod()).isEqualTo("TRANSFER");
        tossApproves(requested.pgOrderId(), 15_000, "계좌이체");

        PaymentSummaryDto confirmed = paymentFacade.confirmPayment(BUYER_ID, requested.pgOrderId(), PAYMENT_KEY, 15_000);

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(confirmed.pointAmount()).isEqualTo(10_000);
        assertThat(confirmed.walletUsedAmount()).isEqualTo(40_000);
        assertThat(buyerWallet().getBalance()).isZero();
        assertThat(buyerWallet().getPointBalance()).isZero();
        assertThat(escrowBalance()).isEqualTo(TOTAL);
        assertLedgerMatched();
    }

    @Test
    @DisplayName("포인트 전액: 고른 수단과 관계없이 결제창 없이 결제되고, 환불하면 전부 포인트로 돌아간다")
    void pointOnly() {
        givenPoint(50_000);

        PaymentResultDto result = paymentFacade.requestPayment(twoOrders("CARD", 50_000));

        assertThat(result.isPaid()).isTrue();
        assertThat(buyerWallet().getPointBalance()).isZero();
        assertThat(holdingOf(ORDER_A).getShare()).isEqualTo(new PaymentShare(33_000, 0, 0));
        assertThat(chargeRepository.count()).isZero();

        walletFacade.refund(7_001L, ORDER_B, 17_000);

        assertThat(buyerWallet().getPointBalance()).isEqualTo(17_000);
        assertThat(buyerWallet().getBalance()).isZero();
        assertLedgerMatched();
    }

    // ===== 직접 결제 =====

    @Test
    @DisplayName("계좌이체 직접 결제: 결제한 돈이 예치금을 거치지 않고 ESCROW 로 들어가고, 실제 결제수단이 남으며 원장 대사가 일치한다")
    void directTransfer() {
        givenBalance(20_000);                                            // 예치금이 있어도 쓰지 않는다

        PaymentSummaryDto confirmed = payDirectly("TRANSFER", 0, "계좌이체");

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(confirmed.payMethod()).isEqualTo(PayMethod.TRANSFER);
        assertThat(confirmed.pgAmount()).isEqualTo(TOTAL);
        assertThat(confirmed.walletUsedAmount()).isZero();
        assertThat(confirmed.pgMethod()).isEqualTo("계좌이체");
        assertThat(buyerWallet().getBalance()).isEqualTo(20_000);        // 그대로
        assertThat(escrowBalance()).isEqualTo(TOTAL);

        Charge charge = directCharge();
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.USED);
        assertThat(charge.getRemainingAmount()).isZero();                // 예치금으로 들어온 적이 없다
        assertThat(holdingOf(ORDER_A).getShare()).isEqualTo(new PaymentShare(0, 0, 33_000));
        assertThat(holdingOf(ORDER_B).getShare()).isEqualTo(new PaymentShare(0, 0, 17_000));
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyString());
        assertLedgerMatched();
    }

    @Test
    @DisplayName("계좌이체를 골랐는데 실제로는 카드로 결제해도 거절하지 않고, 실제 결제수단(카드)을 남긴다")
    void directTransferPaidByCardIsAccepted() {
        PaymentSummaryDto confirmed = payDirectly("TRANSFER", 0, "카드");

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(confirmed.payMethod()).isEqualTo(PayMethod.TRANSFER);  // 고른 수단
        assertThat(confirmed.pgMethod()).isEqualTo("카드");                // 실제 수단
        assertThat(directCharge().getMethod()).isEqualTo("카드");
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("포인트 + 카드 직접 결제: 주문 1건을 환불하면 포인트 몫은 포인트로, 카드 몫은 토스 부분 취소로 돌아가고 예치금은 늘지 않는다")
    void refundPointAndDirectCard() {
        givenPoint(10_000);
        payDirectly("CARD", 10_000, "간편결제");
        assertThat(holdingOf(ORDER_A).getShare()).isEqualTo(new PaymentShare(6_600, 0, 26_400));
        assertThat(holdingOf(ORDER_B).getShare()).isEqualTo(new PaymentShare(3_400, 0, 13_600));
        assertThat(payment().getPgMethod()).isEqualTo("간편결제");

        walletFacade.refund(7_001L, ORDER_A, 33_000);

        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), eq(26_400L), contains(String.valueOf(ORDER_A)));
        assertThat(buyerWallet().getPointBalance()).isEqualTo(6_600);
        assertThat(buyerWallet().getBalance()).isZero();                 // 카드로 낸 돈이 출금할 수 있는 예치금이 되지 않는다
        assertThat(escrowBalance()).isEqualTo(17_000);
        Charge charge = directCharge();
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.USED);
        assertThat(charge.getCanceledAmount()).isEqualTo(26_400);
        assertLedgerMatched();
    }

    @Test
    @DisplayName("직접 결제한 주문을 모두 환불하면 주문마다 그 몫만 부분 취소하고, 결제 건은 CANCELED 가 되며 원장 대사가 일치한다")
    void refundAllOrdersOfDirectPayment() {
        payDirectly("CARD", 0, "카드");

        walletFacade.refund(7_001L, ORDER_A, 33_000);
        walletFacade.refund(7_002L, ORDER_B, 17_000);

        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), eq(33_000L), anyString());
        verify(tossPaymentsClient).cancel(eq(PAYMENT_KEY), anyString(), eq(17_000L), anyString());
        Charge charge = directCharge();
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CANCELED);
        assertThat(charge.getCanceledAmount()).isEqualTo(TOTAL);
        assertThat(escrowBalance()).isZero();
        assertThat(buyerWallet().getBalance()).isZero();
        assertThat(payment().isAllRefunded()).isTrue();
        assertLedgerMatched();
    }

    @Test
    @DisplayName("같은 환불이 다시 와도 토스 취소와 돈의 이동은 한 번만 일어난다")
    void refundIsIdempotent() {
        payDirectly("CARD", 0, "카드");

        walletFacade.refund(7_001L, ORDER_A, 33_000);
        walletFacade.refund(7_001L, ORDER_A, 33_000);

        verify(tossPaymentsClient, times(1)).cancel(eq(PAYMENT_KEY), anyString(), eq(33_000L), anyString());
        assertThat(directCharge().getCanceledAmount()).isEqualTo(33_000);
        assertThat(escrowBalance()).isEqualTo(17_000);
    }

    @Test
    @DisplayName("토스 부분 취소가 실패하면 환불 전체가 되돌아가 홀딩 · ESCROW · 포인트 · 결제 건이 그대로다")
    void tossCancelFailureRollsBackRefund() {
        givenPoint(10_000);
        payDirectly("CARD", 10_000, "카드");
        when(tossPaymentsClient.cancel(eq(PAYMENT_KEY), anyString(), anyLong(), anyString()))
                .thenThrow(new PgApiException(PG_CANCEL_FAILED, "PROVIDER_ERROR", "일시적인 오류"));

        assertThatThrownBy(() -> walletFacade.refund(7_001L, ORDER_A, 33_000))
                .isInstanceOf(PgApiException.class);

        assertThat(holdingOf(ORDER_A).getStatus()).isEqualTo(HoldingStatus.HELD);
        assertThat(escrowBalance()).isEqualTo(TOTAL);
        assertThat(buyerWallet().getPointBalance()).isZero();            // 포인트 환불도 함께 되돌아갔다
        assertThat(directCharge().getCanceledAmount()).isZero();
        assertLedgerMatched();
    }

    // ===== 결제창에 있는 동안 조건이 바뀐 경우 =====

    @Test
    @DisplayName("결제창에 있는 동안 포인트가 줄었으면 토스 승인 전에 거절해 돈이 움직이지 않는다")
    void pointDecreasedWhileInPgWindow() {
        givenPoint(10_000);
        PaymentResultDto requested = paymentFacade.requestPayment(twoOrders("CARD", 10_000));
        pointSpentElsewhere(4_000);

        assertThatThrownBy(() -> paymentFacade.confirmPayment(BUYER_ID, requested.pgOrderId(), PAYMENT_KEY, 40_000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(INSUFFICIENT_POINT);

        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyLong(), anyString());
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.CHARGING);   // 포인트를 줄여 다시 결제할 수 있다
        assertThat(holdingRepository.count()).isZero();
        assertThat(escrowBalance()).isZero();
    }

    @Test
    @DisplayName("결제수단을 바꿔 다시 결제 요청한 뒤 예전 결제창으로 승인하면 토스 승인 전에 CHARGE_SUPERSEDED 로 거절한다")
    void supersededPgWindowIsRejected() {
        PaymentResultDto transfer = paymentFacade.requestPayment(twoOrders("TRANSFER", 0));
        PaymentResultDto card = paymentFacade.requestPayment(twoOrders("CARD", 0));
        assertThat(card.pgOrderId()).isNotEqualTo(transfer.pgOrderId());

        assertThatThrownBy(() -> paymentFacade.confirmPayment(BUYER_ID, transfer.pgOrderId(), PAYMENT_KEY, TOTAL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CHARGE_SUPERSEDED);
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyLong(), anyString());

        tossApproves(card.pgOrderId(), TOTAL, "카드");
        PaymentSummaryDto confirmed = paymentFacade.confirmPayment(BUYER_ID, card.pgOrderId(), PAYMENT_KEY, TOTAL);
        assertThat(confirmed.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(confirmed.payMethod()).isEqualTo(PayMethod.CARD);
    }

    @Test
    @DisplayName("결제수단을 보내지 않는 기존 요청은 지금처럼 전액 예치금 결제로 동작한다")
    void requestWithoutPayMethodIsDepositPayment() {
        givenBalance(60_000);
        givenPoint(10_000);                                              // 포인트가 있어도 쓰지 않는다
        PaymentRequestDto legacy = new PaymentRequestDto(ORDER_GROUP_ID, BUYER_ID, TOTAL,
                List.of(new PaymentRequestDto.Item(ORDER_A, SELLER_ID, 30_000, 3_000),
                        new PaymentRequestDto.Item(ORDER_B, SELLER_ID, 17_000, 0)));

        PaymentResultDto result = paymentFacade.requestPayment(legacy);

        assertThat(result.isPaid()).isTrue();
        assertThat(buyerWallet().getBalance()).isEqualTo(10_000);
        assertThat(buyerWallet().getPointBalance()).isEqualTo(10_000);
        assertThat(holdingOf(ORDER_A).getShare()).isEqualTo(PaymentShare.depositOnly(33_000));
        assertLedgerMatched();
    }

    // ===== 대사가 잡아내는 불일치 =====

    @Test
    @DisplayName("직접 결제의 ESCROW 입금 기록이 없으면 홀딩 · 결제 건 · ESCROW 잔액 검사에서 모두 잡힌다")
    void missingPgRecordIsDetected() {
        payDirectly("CARD", 0, "카드");
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                        "delete from wallet_transactions where type = 'PG_HOLD_RECEIVE' and reference_id = ?1")
                .setParameter(1, ORDER_A).executeUpdate());

        ReconciliationRun run = reconcile();

        assertThat(run.getStatus()).isEqualTo(MISMATCHED);
        assertThat(mismatchRepository.findByRun(run)).extracting(ReconciliationMismatch::getType)
                .containsExactlyInAnyOrder(HOLDING_LEDGER_MISMATCH, CHARGE_LEDGER_MISMATCH, BALANCE_LEDGER_MISMATCH);
    }

    @Test
    @DisplayName("직접 결제 건의 취소 누계가 환불 기록과 다르면 결제 건 검사에서 잡힌다")
    void canceledAmountMismatchIsDetected() {
        payDirectly("CARD", 0, "카드");
        walletFacade.refund(7_001L, ORDER_A, 33_000);
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "update charge set canceled_amount = 30000 where purpose = 'DIRECT_PAYMENT'").executeUpdate());

        ReconciliationRun run = reconcile();

        assertThat(run.getStatus()).isEqualTo(MISMATCHED);
        assertThat(mismatchRepository.findByRun(run)).extracting(ReconciliationMismatch::getType)
                .containsExactly(CHARGE_LEDGER_MISMATCH);
    }
}
