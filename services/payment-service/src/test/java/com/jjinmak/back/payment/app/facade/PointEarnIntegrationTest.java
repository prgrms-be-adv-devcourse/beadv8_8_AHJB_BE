package com.jjinmak.back.payment.app.facade;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.back.payment.app.dto.HoldingPageDto;
import com.jjinmak.back.payment.app.dto.HoldingRole;
import com.jjinmak.back.payment.app.dto.HoldingSummaryDto;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Holding;
import com.jjinmak.back.payment.domain.ReconciliationMismatch;
import com.jjinmak.back.payment.domain.ReconciliationRun;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.WalletTransaction;
import com.jjinmak.back.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.payment.domain.enums.WalletAccount;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.in.scheduler.ReconciliationScheduler;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import com.jjinmak.back.payment.out.repository.ReconciliationMismatchRepository;
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

import static com.jjinmak.back.payment.domain.enums.MismatchType.BALANCE_LEDGER_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.MismatchType.HOLDING_LEDGER_MISMATCH;
import static com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus.MATCHED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationRunStatus.MISMATCHED;
import static com.jjinmak.back.payment.domain.enums.ReconciliationType.LEDGER;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.ORDER;
import static com.jjinmak.back.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.POINT_EARN;
import static com.jjinmak.back.payment.exception.WalletErrorCode.HOLDING_ALREADY_REFUNDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 구매 확정 시 포인트 적립을 실제 PostgreSQL 에서 확인한다: 결제 → 구매 확정 → 구매자 포인트 · 원장 · 홀딩 → 원장 대사.
 * 적립률은 설정값(1%)이다.
 *
 * 테스트마다 테이블을 비우고 시스템 지갑(ESCROW, FEE)만 다시 만든다.
 *
 * 주문 묶음 (twoOrders): 주문 1001 = 낙찰 30,000 + 배송 3,000 = 33,000 / 주문 1002 = 낙찰 17,000 → 총액 50,000
 */
class PointEarnIntegrationTest extends PaymentIntegrationTestSupport {

    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long ORDER_GROUP_ID = 500L;
    private static final long ORDER_A = 1_001L;      // 낙찰 30,000 + 배송 3,000
    private static final long ORDER_B = 1_002L;      // 낙찰 17,000
    private static final long TOTAL = 50_000L;
    private static final String PAYMENT_KEY = "pk-earn";

    private static final String TABLES = "reconciliation_mismatch, reconciliation_run, wallet_transactions, holding, "
            + "charge, withdrawal, payment_item, payment, toss_webhook_event, wallet, wallet_member";

    @Autowired PaymentFacade paymentFacade;
    @Autowired WalletFacade walletFacade;
    @Autowired LedgerRecorder ledgerRecorder;
    @Autowired ReconciliationScheduler reconciliationScheduler;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletTransactionRepository walletTransactionRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired HoldingRepository holdingRepository;
    @Autowired ReconciliationRunRepository runRepository;
    @Autowired ReconciliationMismatchRepository mismatchRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager entityManager;

    private Long buyerWalletId;

    @BeforeEach
    void setUp() {
        resetDatabase();
        buyerWalletId = createMemberWithWallet(BUYER_ID);
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

    private Long createMemberWithWallet(long memberId) {
        return transactionTemplate.execute(status -> {
            WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
            return walletRepository.save(Wallet.createUserWallet(member)).getId();
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

    private static PaymentRequestDto twoOrders(long orderGroupId, long orderA, long orderB, String payMethod, long pointUseAmount) {
        return new PaymentRequestDto(orderGroupId, BUYER_ID, TOTAL,
                List.of(new PaymentRequestDto.Item(orderA, SELLER_ID, 30_000, 3_000),
                        new PaymentRequestDto.Item(orderB, SELLER_ID, 17_000, 0)),
                payMethod, pointUseAmount);
    }

    // 주문 1001 · 1002 를 이 수단과 포인트 사용액으로 결제한다. 직접 결제면 토스가 actualMethod 로 승인했다고 답하게 한다
    private void pay(String payMethod, long pointUseAmount, String actualMethod) {
        PaymentResultDto requested = paymentFacade.requestPayment(
                twoOrders(ORDER_GROUP_ID, ORDER_A, ORDER_B, payMethod, pointUseAmount));
        if (requested.isPaid()) return;

        long amount = requested.chargeAmount();
        when(tossPaymentsClient.confirm(eq(PAYMENT_KEY), eq(requested.pgOrderId()), eq(amount), anyString()))
                .thenReturn(new TossPaymentResponse(PAYMENT_KEY, requested.pgOrderId(), "찐막 주문 결제", "DONE",
                        amount, amount, actualMethod, null, "2026-11-18T10:00:00+09:00", null, null, null));
        paymentFacade.confirmPayment(BUYER_ID, requested.pgOrderId(), PAYMENT_KEY, amount);
    }

    // ===== 결과 읽기 =====

    private Wallet buyerWallet() {
        return walletRepository.findByHolderId(BUYER_ID).orElseThrow();
    }

    private Holding holdingOf(long orderId) {
        return holdingRepository.findByOrderId(orderId).orElseThrow();
    }

    private List<WalletTransaction> earnRecords() {
        return walletTransactionRepository.findByReferenceTypeAndTypeInAndReferenceIdIn(
                ORDER, List.of(POINT_EARN), List.of(ORDER_A, ORDER_B));
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

    // ===== 적립 =====

    @Test
    @DisplayName("예치금 결제: 구매 확정하면 배송비를 뺀 낙찰가의 1% 가 포인트로 적립되고, 홀딩과 원장에 남으며 원장 대사가 일치한다")
    void depositPaymentEarnsOnConfirm() {
        givenBalance(60_000);
        pay("DEPOSIT", 0, null);
        assertThat(buyerWallet().getPointBalance()).isZero();            // 결제만으로는 적립되지 않는다

        walletFacade.confirmHolding(ORDER_A);

        assertThat(buyerWallet().getPointBalance()).isEqualTo(300);      // 33,000 중 낙찰가 30,000 의 1%
        assertThat(buyerWallet().getBalance()).isEqualTo(10_000);        // 예치금은 그대로
        Holding holding = holdingOf(ORDER_A);
        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.CONFIRMED);
        assertThat(holding.getEarnedPoint()).isEqualTo(300);
        assertThat(holdingOf(ORDER_B).getEarnedPoint()).isZero();        // 아직 확정하지 않은 주문

        assertThat(earnRecords()).hasSize(1);
        WalletTransaction record = earnRecords().get(0);
        assertThat(record.getAccount()).isEqualTo(WalletAccount.POINT);
        assertThat(record.getAmount()).isEqualTo(300);
        assertThat(record.getBalanceAfter()).isEqualTo(300);
        assertLedgerMatched();
    }

    @Test
    @DisplayName("같은 구매 확정을 다시 받아도, 정산이 끝난 뒤에 다시 받아도 한 번만 적립한다")
    void confirmTwiceEarnsOnce() {
        givenBalance(60_000);
        pay("DEPOSIT", 0, null);

        walletFacade.confirmHolding(ORDER_B);
        walletFacade.confirmHolding(ORDER_B);
        walletFacade.releaseHolding(ORDER_B);
        walletFacade.confirmHolding(ORDER_B);

        assertThat(buyerWallet().getPointBalance()).isEqualTo(170);      // 17,000 의 1%
        assertThat(earnRecords()).hasSize(1);
        assertThat(holdingOf(ORDER_B).getStatus()).isEqualTo(HoldingStatus.RELEASED);
        assertLedgerMatched();
    }

    @Test
    @DisplayName("예치금 부족분을 충전해서 결제해도 예치금으로 낸 금액 전부가 적립 대상이다")
    void toppedUpDepositEarns() {
        givenBalance(20_000);
        pay("DEPOSIT", 0, "계좌이체");                                    // 30,000 충전 후 결제

        walletFacade.confirmHolding(ORDER_A);
        walletFacade.confirmHolding(ORDER_B);

        assertThat(buyerWallet().getPointBalance()).isEqualTo(300 + 170);
        assertLedgerMatched();
    }

    @Test
    @DisplayName("계좌이체 직접 결제는 적립하고, 카드 · 간편결제 직접 결제는 적립하지 않는다")
    void directTransferEarnsButCardDoesNot() {
        pay("TRANSFER", 0, "계좌이체");
        walletFacade.confirmHolding(ORDER_A);
        assertThat(buyerWallet().getPointBalance()).isEqualTo(300);
        assertThat(holdingOf(ORDER_A).getEarnedPoint()).isEqualTo(300);

        PaymentResultDto card = paymentFacade.requestPayment(twoOrders(501L, 2_001L, 2_002L, "CARD", 0));
        when(tossPaymentsClient.confirm(eq("pk-card"), eq(card.pgOrderId()), eq(TOTAL), anyString()))
                .thenReturn(new TossPaymentResponse("pk-card", card.pgOrderId(), "찐막 주문 결제", "DONE",
                        TOTAL, TOTAL, "간편결제", null, "2026-11-18T10:00:00+09:00", null, null, null));
        paymentFacade.confirmPayment(BUYER_ID, card.pgOrderId(), "pk-card", TOTAL);
        walletFacade.confirmHolding(2_001L);

        assertThat(buyerWallet().getPointBalance()).isEqualTo(300);      // 그대로
        assertThat(holdingOf(2_001L).getStatus()).isEqualTo(HoldingStatus.CONFIRMED);
        assertThat(holdingOf(2_001L).getEarnedPoint()).isZero();
        assertLedgerMatched();
    }

    @Test
    @DisplayName("계좌이체를 골랐지만 결제창에서 카드로 결제했으면 적립하지 않는다")
    void selectedTransferButPaidByCardDoesNotEarn() {
        pay("TRANSFER", 0, "카드");

        walletFacade.confirmHolding(ORDER_A);

        assertThat(buyerWallet().getPointBalance()).isZero();
        assertThat(earnRecords()).isEmpty();
        assertLedgerMatched();
    }

    @Test
    @DisplayName("적립받은 포인트는 다음 결제에 쓸 수 있고, 포인트로 낸 금액에는 다시 적립되지 않는다")
    void earnedPointIsUsableAndNotEarnedAgain() {
        givenBalance(100_000);
        pay("DEPOSIT", 0, null);
        walletFacade.confirmHolding(ORDER_A);
        walletFacade.confirmHolding(ORDER_B);
        assertThat(buyerWallet().getPointBalance()).isEqualTo(470);

        // 주문 2001 (33,000) · 2002 (17,000) 를 포인트 470 + 예치금 49,530 으로 결제
        PaymentResultDto second = paymentFacade.requestPayment(twoOrders(501L, 2_001L, 2_002L, "DEPOSIT", 470));
        assertThat(second.isPaid()).isTrue();
        assertThat(buyerWallet().getPointBalance()).isZero();

        walletFacade.confirmHolding(2_001L);

        // 주문 2001 의 포인트 몫 = 470 × 33,000 / 50,000 = 310 → 예치금 몫 32,690 → 낙찰가 몫 29,718 → 297
        Holding holding = holdingOf(2_001L);
        assertThat(holding.getPointAmount()).isEqualTo(310);
        assertThat(holding.getEarnedPoint()).isEqualTo(297);
        assertThat(buyerWallet().getPointBalance()).isEqualTo(297);
        assertLedgerMatched();
    }

    @Test
    @DisplayName("환불된 주문은 구매 확정할 수 없어 적립되지 않고, 같은 묶음의 다른 주문은 구매 확정하면 적립된다")
    void refundedOrderDoesNotEarn() {
        givenBalance(60_000);
        pay("DEPOSIT", 0, null);
        walletFacade.refund(7_001L, ORDER_A, 33_000);

        assertThatThrownBy(() -> walletFacade.confirmHolding(ORDER_A))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(HOLDING_ALREADY_REFUNDED);
        walletFacade.confirmHolding(ORDER_B);

        assertThat(buyerWallet().getPointBalance()).isEqualTo(170);
        assertThat(holdingOf(ORDER_A).getEarnedPoint()).isZero();
        assertLedgerMatched();
    }

    @Test
    @DisplayName("홀딩 조회: 적립 포인트는 구매자에게만 보이고 판매자에게는 0 이다")
    void earnedPointIsVisibleToBuyerOnly() {
        givenBalance(60_000);
        pay("DEPOSIT", 0, null);
        walletFacade.confirmHolding(ORDER_A);

        HoldingPageDto buyerView = walletFacade.getHoldings(BUYER_ID, HoldingRole.BUYER, HoldingStatus.CONFIRMED, 0, 10);
        HoldingPageDto sellerView = walletFacade.getHoldings(SELLER_ID, HoldingRole.SELLER, HoldingStatus.CONFIRMED, 0, 10);

        assertThat(buyerView.content()).extracting(HoldingSummaryDto::earnedPoint).containsExactly(300L);
        assertThat(sellerView.content()).extracting(HoldingSummaryDto::earnedPoint).containsExactly(0L);
    }

    // ===== 대사가 잡아내는 불일치 =====

    @Test
    @DisplayName("적립 기록이 없어지면 홀딩 검사와 구매자 포인트 잔액 검사에서 잡힌다")
    void missingEarnRecordIsDetected() {
        givenBalance(60_000);
        pay("DEPOSIT", 0, null);
        walletFacade.confirmHolding(ORDER_A);
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "delete from wallet_transactions where type = 'POINT_EARN' and reference_id = ?1")
                .setParameter(1, ORDER_A).executeUpdate());

        ReconciliationRun run = reconcile();

        assertThat(run.getStatus()).isEqualTo(MISMATCHED);
        List<ReconciliationMismatch> mismatches = mismatchRepository.findByRun(run);
        assertThat(mismatches).extracting(ReconciliationMismatch::getType)
                .containsExactlyInAnyOrder(HOLDING_LEDGER_MISMATCH, BALANCE_LEDGER_MISMATCH);
        assertThat(mismatches).extracting(ReconciliationMismatch::getTargetKey)
                .containsExactlyInAnyOrder(String.valueOf(ORDER_A), buyerWalletId + ":POINT");
    }
}
