package com.jjinmak.back.payment.app.facade;

import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.HoldingRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.back.payment.out.repository.WalletMemberRepository;
import com.jjinmak.back.payment.out.repository.WalletRepository;
import com.jjinmak.back.payment.support.PaymentIntegrationTestSupport;
import com.jjinmak.back.shared.payment.port.PaymentPort;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.jjinmak.back.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 주문 그룹의 "첫" 결제 요청이 동시에 올 때를 실제 PostgreSQL 에서 확인한다 (PaymentPort.requestPayment).
 *
 * Payment 가 아직 없으면 FOR UPDATE 로 잠글 행이 없어 두 요청이 모두 새로 만들려 하고, 늦은 쪽은 유니크 제약
 * (uk_payment_order_group)에 걸린다. 이때 500 이 아니라 먼저 만들어진 결제와 같은 결과가 나가야 한다.
 * (2026-11-21 배포 서버 k6 double-order 에서 500 2건으로 드러난 경우)
 *
 * 주문 묶음: 주문 1001 = 33,000 / 주문 1002 = 17,000 → 총액 50,000
 */
class PaymentRequestRaceIntegrationTest extends PaymentIntegrationTestSupport {

    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long ORDER_GROUP_ID = 500L;
    private static final long TOTAL = 50_000L;
    private static final int STORM = 4;      // 커넥션 풀(5)보다 작게

    private static final String TABLES = "reconciliation_mismatch, reconciliation_run, wallet_transactions, holding, "
            + "charge, withdrawal, payment_item, payment, toss_webhook_event, wallet, wallet_member";

    @Autowired PaymentPort paymentPort;
    @Autowired PaymentFacade paymentFacade;
    @Autowired LedgerRecorder ledgerRecorder;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired HoldingRepository holdingRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager entityManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(STORM);

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

    private static PaymentRequestDto twoOrders(String payMethod) {
        return new PaymentRequestDto(ORDER_GROUP_ID, BUYER_ID, TOTAL,
                List.of(new PaymentRequestDto.Item(1_001L, SELLER_ID, 30_000, 3_000),
                        new PaymentRequestDto.Item(1_002L, SELLER_ID, 17_000, 0)),
                payMethod, 0);
    }

    // 먼저 온 요청: Payment 를 INSERT 한 뒤 커밋하지 않고 머문다. inserted 로 알리고 release 가 풀리면 커밋한다
    private CompletableFuture<PaymentResultDto> requestButHoldCommit(PaymentRequestDto dto, CountDownLatch inserted,
                                                                     CountDownLatch release) {
        return CompletableFuture.supplyAsync(() -> transactionTemplate.execute(status -> {
            PaymentResultDto result = paymentFacade.requestPayment(dto);
            entityManager.flush();
            inserted.countDown();
            await(release);
            return result;
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

    private long buyerBalance() {
        return walletRepository.findByHolderId(BUYER_ID).orElseThrow().getBalance();
    }

    private Payment payment() {
        return paymentRepository.findByOrderGroupId(ORDER_GROUP_ID).orElseThrow();
    }

    // ===== 늦은 요청이 유니크 제약에 걸리는 순서를 직접 만든다 =====

    @Test
    @DisplayName("예치금 결제: 먼저 온 요청이 커밋하기 전에 온 두 번째 요청은 기다렸다가 같은 결제의 PAID 를 돌려주고, 돈은 한 번만 나간다")
    void depositPayment_lateRequestGetsSamePaidResult() throws Exception {
        givenBalance(TOTAL * 2);                                         // 두 번 결제될 수 있을 만큼
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<PaymentResultDto> first = requestButHoldCommit(twoOrders("DEPOSIT"), inserted, release);
        await(inserted);                                                 // 첫 요청의 Payment 는 아직 커밋 전이라 다른 요청에는 안 보인다
        CompletableFuture<PaymentResultDto> second =
                CompletableFuture.supplyAsync(() -> paymentPort.requestPayment(twoOrders("DEPOSIT")), executor);

        Thread.sleep(700);
        assertThat(second).isNotDone();                                  // 두 번째의 INSERT 는 유니크 인덱스에서 기다린다
        release.countDown();

        PaymentResultDto firstResult = first.get(20, TimeUnit.SECONDS);
        PaymentResultDto secondResult = second.get(20, TimeUnit.SECONDS);   // 고치기 전에는 여기서 DataIntegrityViolationException
        assertThat(firstResult.status()).isEqualTo(PaymentResultDto.Status.PAID);
        assertThat(secondResult.status()).isEqualTo(PaymentResultDto.Status.PAID);
        assertThat(secondResult.paymentId()).isEqualTo(firstResult.paymentId());

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(holdingRepository.count()).isEqualTo(2);              // 주문 2건의 홀딩이 한 벌만
        assertThat(buyerBalance()).isEqualTo(TOTAL);                     // 100,000 에서 한 번만 차감
    }

    @Test
    @DisplayName("직접 결제: 두 번째 요청은 새 결제창을 만들지 않고 먼저 만들어진 결제창(같은 주문번호)을 돌려준다")
    void directPayment_lateRequestGetsSameWindow() throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<PaymentResultDto> first = requestButHoldCommit(twoOrders("CARD"), inserted, release);
        await(inserted);
        CompletableFuture<PaymentResultDto> second =
                CompletableFuture.supplyAsync(() -> paymentPort.requestPayment(twoOrders("CARD")), executor);

        Thread.sleep(700);
        assertThat(second).isNotDone();
        release.countDown();

        PaymentResultDto firstResult = first.get(20, TimeUnit.SECONDS);
        PaymentResultDto secondResult = second.get(20, TimeUnit.SECONDS);
        assertThat(firstResult.status()).isEqualTo(PaymentResultDto.Status.NEEDS_PAYMENT);
        assertThat(secondResult.status()).isEqualTo(PaymentResultDto.Status.NEEDS_PAYMENT);
        assertThat(secondResult.paymentId()).isEqualTo(firstResult.paymentId());
        assertThat(secondResult.pgOrderId()).isEqualTo(firstResult.pgOrderId());

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(chargeRepository.count()).isEqualTo(1);               // 결제창(Charge)도 하나만
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.CHARGING);
    }

    // ===== 순서를 정하지 않고 한꺼번에 =====

    @Test
    @DisplayName("예치금 결제 요청이 한꺼번에 여러 개 와도 모두 같은 결제의 PAID 를 받고, 돈은 한 번만 나간다")
    void depositPayment_storm() throws Exception {
        givenBalance(TOTAL * STORM);                                     // 요청 수만큼 결제될 수 있을 만큼
        CountDownLatch start = new CountDownLatch(1);

        List<CompletableFuture<PaymentResultDto>> requests = new ArrayList<>();
        for (int i = 0; i < STORM; i++) {
            requests.add(CompletableFuture.supplyAsync(() -> {
                await(start);
                return paymentPort.requestPayment(twoOrders("DEPOSIT"));
            }, executor));
        }
        start.countDown();

        List<PaymentResultDto> results = new ArrayList<>();
        for (CompletableFuture<PaymentResultDto> request : requests) {
            results.add(request.get(30, TimeUnit.SECONDS));
        }
        assertThat(results).extracting(PaymentResultDto::status).containsOnly(PaymentResultDto.Status.PAID);
        assertThat(results).extracting(PaymentResultDto::paymentId).containsOnly(results.get(0).paymentId());

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(holdingRepository.count()).isEqualTo(2);
        assertThat(buyerBalance()).isEqualTo(TOTAL * (STORM - 1));
    }
}
