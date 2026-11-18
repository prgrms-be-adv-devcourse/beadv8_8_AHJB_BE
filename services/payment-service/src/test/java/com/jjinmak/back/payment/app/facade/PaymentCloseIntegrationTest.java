package com.jjinmak.back.payment.app.facade;

import com.jjinmak.back.payment.app.dto.PaymentSummaryDto;
import com.jjinmak.back.payment.app.usecase.PaymentCloseUseCase;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.PaymentCloseReason;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.back.payment.out.repository.WalletMemberRepository;
import com.jjinmak.back.payment.out.repository.WalletRepository;
import com.jjinmak.back.payment.support.PaymentIntegrationTestSupport;
import com.jjinmak.back.shared.payment.port.PaymentPort;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.payment.dto.PaymentCloseResultDto;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_CLOSED;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_FORBIDDEN;
import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 주문이 버린 결제 닫기를 실제 PostgreSQL 에서 확인한다 (PaymentPort.closePayment → 닫기 유스케이스).
 * 닫힌 뒤 승인 · 결제 요청이 막히는지와, 닫기 · 승인 · 결제 요청이 Payment 행 락(FOR UPDATE)으로 서로 끼어들지 못하는지를 본다.
 * 토스는 응답만 가짜로 바꾸고(승인 응답을 원하는 만큼 붙잡아 둘 수 있다), 트랜잭션 · 락은 운영 코드 그대로 돈다.
 *
 * 주문 묶음: 주문 1001 = 33,000 / 주문 1002 = 17,000 → 총액 50,000, 카드 직접 결제
 */
class PaymentCloseIntegrationTest extends PaymentIntegrationTestSupport {

    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long OTHER_ID = 3L;
    private static final long ORDER_GROUP_ID = 500L;
    private static final long TOTAL = 50_000L;
    private static final String PAYMENT_KEY = "pk-close";

    private static final String TABLES = "reconciliation_mismatch, reconciliation_run, wallet_transactions, holding, "
            + "charge, withdrawal, payment_item, payment, toss_webhook_event, wallet, wallet_member";

    @Autowired PaymentPort paymentPort;
    @Autowired PaymentFacade paymentFacade;
    @Autowired PaymentCloseUseCase paymentCloseUseCase;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired HoldingRepository holdingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager entityManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @BeforeEach
    void setUp() {
        resetDatabase();
        createMemberWithWallet(BUYER_ID);
        createMemberWithWallet(SELLER_ID);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
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

    private static PaymentRequestDto twoOrders() {
        return new PaymentRequestDto(ORDER_GROUP_ID, BUYER_ID, TOTAL,
                List.of(new PaymentRequestDto.Item(1_001L, SELLER_ID, 30_000, 3_000),
                        new PaymentRequestDto.Item(1_002L, SELLER_ID, 17_000, 0)),
                "CARD", 0);
    }

    // 카드 직접 결제로 결제창을 띄운다 (Payment CHARGING + Charge REQUESTED)
    private PaymentResultDto openCardWindow() {
        PaymentResultDto requested = paymentPort.requestPayment(twoOrders());
        assertThat(requested.status()).isEqualTo(PaymentResultDto.Status.NEEDS_PAYMENT);
        return requested;
    }

    private static TossPaymentResponse approved(String pgOrderId, long amount) {
        return new TossPaymentResponse(PAYMENT_KEY, pgOrderId, "찐막 주문 결제", "DONE",
                amount, amount, "카드", null, "2026-11-18T10:00:00+09:00", null, null, null);
    }

    private void tossApproves(PaymentResultDto requested) {
        when(tossPaymentsClient.confirm(eq(PAYMENT_KEY), eq(requested.pgOrderId()), eq(requested.chargeAmount()), anyString()))
                .thenReturn(approved(requested.pgOrderId(), requested.chargeAmount()));
    }

    // 토스 승인 호출이 들어오면 entered 를 알리고, release 가 풀릴 때까지 응답하지 않는다 (승인 트랜잭션이 락을 쥔 채 머문다)
    private void tossApprovesAfter(PaymentResultDto requested, CountDownLatch entered, CountDownLatch release) {
        when(tossPaymentsClient.confirm(eq(PAYMENT_KEY), eq(requested.pgOrderId()), eq(requested.chargeAmount()), anyString()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
                    return approved(requested.pgOrderId(), requested.chargeAmount());
                });
    }

    private PaymentSummaryDto confirm(PaymentResultDto requested) {
        return paymentFacade.confirmPayment(BUYER_ID, requested.pgOrderId(), PAYMENT_KEY, requested.chargeAmount());
    }

    // 닫기가 Payment 락을 쥔 채 커밋하지 않고 머문다. locked 로 락을 잡았음을 알리고 release 가 풀리면 커밋한다
    private CompletableFuture<Void> closeButHoldCommit(CountDownLatch locked, CountDownLatch release) {
        return CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(status -> {
            paymentCloseUseCase.closeByOrder(ORDER_GROUP_ID, BUYER_ID);
            entityManager.flush();
            locked.countDown();
            await(release);
        }), executor);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // ===== 결과 읽기 =====

    private Payment payment() {
        return paymentRepository.findByOrderGroupId(ORDER_GROUP_ID).orElseThrow();
    }

    private Charge chargeOf(String pgOrderId) {
        return chargeRepository.findByPgOrderId(pgOrderId).orElseThrow();
    }

    private static Object errorCodeOf(CompletableFuture<?> future) throws InterruptedException, TimeoutException {
        try {
            future.get(20, TimeUnit.SECONDS);
            throw new AssertionError("예외가 나야 하는데 정상 종료됨");
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(BusinessException.class);
            return ((BusinessException) e.getCause()).getErrorCode();
        }
    }

    // ===== 닫기와 그 뒤 =====

    @Test
    @DisplayName("결제창이 떠 있는 결제를 닫으면 결제는 FAILED, 결제창은 ABORTED 가 되고 같은 요청을 다시 보내도 결과가 같다")
    void close_thenSameResultAgain() {
        PaymentResultDto requested = openCardWindow();

        PaymentCloseResultDto first = paymentPort.closePayment(ORDER_GROUP_ID, BUYER_ID);
        PaymentCloseResultDto second = paymentPort.closePayment(ORDER_GROUP_ID, BUYER_ID);

        assertThat(first.status()).isEqualTo(PaymentCloseResultDto.Status.CLOSED);
        assertThat(second).isEqualTo(first);
        Payment payment = payment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo(PaymentCloseReason.CLOSED_BY_ORDER.message());
        Charge charge = chargeOf(requested.pgOrderId());
        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.ABORTED);
        assertThat(charge.getFailureCode()).isEqualTo(PaymentCloseReason.CLOSED_BY_ORDER.code());
    }

    @Test
    @DisplayName("닫힌 결제의 결제창으로 승인하면 토스를 부르지 않고 PAYMENT_CLOSED 로 거절한다")
    void confirmAfterClose_isRejectedBeforeToss() {
        PaymentResultDto requested = openCardWindow();
        paymentPort.closePayment(ORDER_GROUP_ID, BUYER_ID);

        assertThatThrownBy(() -> confirm(requested))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_CLOSED);

        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyLong(), anyString());
        assertThat(holdingRepository.count()).isZero();
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("닫힌 결제의 orderGroupId 로 다시 결제 요청하면 PAYMENT_CLOSED 로 거절하고 새 결제창을 만들지 않는다")
    void requestAfterClose_isRejected() {
        openCardWindow();
        paymentPort.closePayment(ORDER_GROUP_ID, BUYER_ID);

        assertThatThrownBy(() -> paymentPort.requestPayment(twoOrders()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_CLOSED);

        assertThat(chargeRepository.count()).isEqualTo(1);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("이미 결제가 끝났으면 닫지 않고 ALREADY_PAID 를 돌려준다")
    void close_paidPayment() {
        PaymentResultDto requested = openCardWindow();
        tossApproves(requested);
        confirm(requested);

        PaymentCloseResultDto result = paymentPort.closePayment(ORDER_GROUP_ID, BUYER_ID);

        assertThat(result.status()).isEqualTo(PaymentCloseResultDto.Status.ALREADY_PAID);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(chargeOf(requested.pgOrderId()).getStatus()).isEqualTo(ChargeStatus.USED);
        assertThat(holdingRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("결제 요청 전의 주문 그룹이면 NOT_FOUND, 구매자가 아니면 PAYMENT_FORBIDDEN 이고 결제는 그대로다")
    void close_notFound_andForbidden() {
        assertThat(paymentPort.closePayment(ORDER_GROUP_ID, BUYER_ID).status())
                .isEqualTo(PaymentCloseResultDto.Status.NOT_FOUND);

        PaymentResultDto requested = openCardWindow();
        assertThatThrownBy(() -> paymentPort.closePayment(ORDER_GROUP_ID, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_FORBIDDEN);

        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.CHARGING);
        assertThat(chargeOf(requested.pgOrderId()).getStatus()).isEqualTo(ChargeStatus.REQUESTED);
    }

    // ===== 동시에 들어올 때 =====

    @Test
    @DisplayName("승인이 토스를 호출하는 중에 닫기가 오면, 닫기는 승인이 끝날 때까지 기다렸다가 ALREADY_PAID 를 돌려준다")
    void closeDuringConfirm_waitsThenAlreadyPaid() throws Exception {
        PaymentResultDto requested = openCardWindow();
        CountDownLatch tossEntered = new CountDownLatch(1);
        CountDownLatch tossRelease = new CountDownLatch(1);
        tossApprovesAfter(requested, tossEntered, tossRelease);

        CompletableFuture<PaymentSummaryDto> confirming = CompletableFuture.supplyAsync(() -> confirm(requested), executor);
        await(tossEntered);                                              // 승인이 Payment 락을 쥐고 토스 응답을 기다린다
        CompletableFuture<PaymentCloseResultDto> closing =
                CompletableFuture.supplyAsync(() -> paymentPort.closePayment(ORDER_GROUP_ID, BUYER_ID), executor);

        Thread.sleep(700);
        assertThat(closing).isNotDone();                                 // 닫기는 락에서 기다린다
        tossRelease.countDown();

        assertThat(confirming.get(20, TimeUnit.SECONDS).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(closing.get(20, TimeUnit.SECONDS).status()).isEqualTo(PaymentCloseResultDto.Status.ALREADY_PAID);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(chargeOf(requested.pgOrderId()).getStatus()).isEqualTo(ChargeStatus.USED);
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("승인이 오래 걸려 닫기가 락을 기다리다 시간이 넘으면 PAYMENT_IN_PROGRESS 를 던지고, 승인은 그대로 끝난다")
    void closeDuringSlowConfirm_timesOut() throws Exception {
        PaymentResultDto requested = openCardWindow();
        CountDownLatch tossEntered = new CountDownLatch(1);
        CountDownLatch tossRelease = new CountDownLatch(1);
        tossApprovesAfter(requested, tossEntered, tossRelease);

        CompletableFuture<PaymentSummaryDto> confirming = CompletableFuture.supplyAsync(() -> confirm(requested), executor);
        await(tossEntered);

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> paymentPort.closePayment(ORDER_GROUP_ID, BUYER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_IN_PROGRESS);
        long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertThat(waitedMs).isBetween(2_500L, 8_000L);                  // PaymentRepository.LOCK_TIMEOUT_MS = 3초

        tossRelease.countDown();
        assertThat(confirming.get(20, TimeUnit.SECONDS).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        // 승인이 끝난 뒤 다시 닫기를 보내면 결과를 알 수 있다
        assertThat(paymentPort.closePayment(ORDER_GROUP_ID, BUYER_ID).status())
                .isEqualTo(PaymentCloseResultDto.Status.ALREADY_PAID);
    }

    @Test
    @DisplayName("닫기가 먼저 락을 잡았으면, 승인은 닫기가 끝날 때까지 기다렸다가 토스를 부르지 않고 PAYMENT_CLOSED 로 거절한다")
    void confirmDuringClose_waitsThenRejectedBeforeToss() throws Exception {
        PaymentResultDto requested = openCardWindow();
        tossApproves(requested);
        CountDownLatch closeLocked = new CountDownLatch(1);
        CountDownLatch closeRelease = new CountDownLatch(1);

        CompletableFuture<Void> closing = closeButHoldCommit(closeLocked, closeRelease);
        await(closeLocked);                                              // 닫기가 Payment 락을 쥐고 아직 커밋하지 않았다
        CompletableFuture<PaymentSummaryDto> confirming = CompletableFuture.supplyAsync(() -> confirm(requested), executor);

        Thread.sleep(700);
        assertThat(confirming).isNotDone();                              // 승인은 락에서 기다린다. 토스를 아직 부르지 않았다
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyLong(), anyString());
        closeRelease.countDown();
        closing.get(20, TimeUnit.SECONDS);

        assertThat(errorCodeOf(confirming)).isEqualTo(PAYMENT_CLOSED);
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyLong(), anyString());
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(chargeOf(requested.pgOrderId()).getStatus()).isEqualTo(ChargeStatus.ABORTED);
        assertThat(holdingRepository.count()).isZero();
    }

    @Test
    @DisplayName("닫기가 먼저 락을 잡았으면, 같은 orderGroupId 의 결제 요청은 기다렸다가 PAYMENT_CLOSED 로 거절되고 닫힌 결제가 되살아나지 않는다")
    void requestDuringClose_waitsThenRejected() throws Exception {
        openCardWindow();
        CountDownLatch closeLocked = new CountDownLatch(1);
        CountDownLatch closeRelease = new CountDownLatch(1);

        CompletableFuture<Void> closing = closeButHoldCommit(closeLocked, closeRelease);
        await(closeLocked);
        CompletableFuture<PaymentResultDto> requesting =
                CompletableFuture.supplyAsync(() -> paymentPort.requestPayment(twoOrders()), executor);

        Thread.sleep(700);
        assertThat(requesting).isNotDone();
        closeRelease.countDown();
        closing.get(20, TimeUnit.SECONDS);

        assertThat(errorCodeOf(requesting)).isEqualTo(PAYMENT_CLOSED);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(chargeRepository.count()).isEqualTo(1);
        assertThat(chargeRepository.findAll()).allMatch(charge -> charge.getStatus() == ChargeStatus.ABORTED);
        verify(tossPaymentsClient, never()).confirm(any(), any(), anyLong(), any());
    }
}
