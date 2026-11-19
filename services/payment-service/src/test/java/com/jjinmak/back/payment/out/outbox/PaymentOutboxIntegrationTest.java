package com.jjinmak.back.payment.out.outbox;

import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.payment.app.facade.PaymentFacade;
import com.jjinmak.back.payment.app.facade.PaymentOutboxFacade;
import com.jjinmak.back.payment.app.facade.WalletFacade;
import com.jjinmak.back.payment.app.support.LedgerRecorder;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.PaymentOutbox;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.in.scheduler.PaymentOutboxScheduler;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.PaymentOutboxRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.back.payment.out.repository.WalletMemberRepository;
import com.jjinmak.back.payment.out.repository.WalletRepository;
import com.jjinmak.back.payment.support.PaymentIntegrationTestSupport;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import com.jjinmak.contracts.payment.event.WalletReleasedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.jjinmak.back.payment.domain.enums.ReferenceType.PAYMENT;
import static com.jjinmak.back.payment.domain.enums.WalletTransactionType.CHARGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 결제 아웃박스를 실제 PostgreSQL 에서 확인한다:
 * 결제 · 환불 · 정산 지급이 커밋되면 이벤트가 아웃박스에 남고, 전송 스케줄러가 그 행을 Kafka 로 보낸 뒤 발행으로 표시한다.
 *
 * 1초마다 도는 스케줄러는 공통 설정에서 목으로 바뀌어 있으므로, 전송은 같은 파사드로 만든 스케줄러를 직접 호출한다.
 * Kafka 는 띄우지 않고 KafkaTemplate 목으로 무엇을 보냈는지만 본다.
 *
 * 주문 묶음 (twoOrders): 주문 1001 = 낙찰 30,000 + 배송 3,000 = 33,000 / 주문 1002 = 낙찰 17,000 → 총액 50,000
 */
class PaymentOutboxIntegrationTest extends PaymentIntegrationTestSupport {

    private static final long BUYER_ID = 1L;
    private static final long SELLER_ID = 2L;
    private static final long ORDER_GROUP_ID = 500L;
    private static final long ORDER_A = 1_001L;      // 33,000
    private static final long ORDER_B = 1_002L;      // 17,000
    private static final long TOTAL = 50_000L;

    private static final String TABLES = "payment_outbox, reconciliation_mismatch, reconciliation_run, wallet_transactions, "
            + "holding, charge, withdrawal, payment_item, payment, toss_webhook_event, wallet, wallet_member";

    @Autowired PaymentFacade paymentFacade;
    @Autowired WalletFacade walletFacade;
    @Autowired PaymentOutboxFacade paymentOutboxFacade;
    @Autowired LedgerRecorder ledgerRecorder;
    @Autowired EventPublisher eventPublisher;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentOutboxRepository paymentOutboxRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired ObjectMapper objectMapper;
    @PersistenceContext EntityManager entityManager;

    PaymentOutboxScheduler paymentOutboxScheduler;

    @BeforeEach
    void setUp() {
        resetDatabase();
        createMemberWithWallet(BUYER_ID);
        createMemberWithWallet(SELLER_ID);
        paymentOutboxScheduler = new PaymentOutboxScheduler(paymentOutboxFacade);
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

    private static PaymentRequestDto twoOrders() {
        return new PaymentRequestDto(ORDER_GROUP_ID, BUYER_ID, TOTAL,
                List.of(new PaymentRequestDto.Item(ORDER_A, SELLER_ID, 30_000, 3_000),
                        new PaymentRequestDto.Item(ORDER_B, SELLER_ID, 17_000, 0)),
                "DEPOSIT", 0);
    }

    // 예치금으로 주문 묶음을 결제하고, 결제 완료 이벤트의 아웃박스는 비워 둔다 (그 뒤의 이벤트만 보려고)
    private void givenPaidOrders() {
        givenBalance(TOTAL);
        paymentFacade.requestPayment(twoOrders());
        paymentOutboxRepository.deleteAllInBatch();
    }

    private void kafkaAcceptsEverything() {
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(anyString(), anyString(), any());
    }

    private void kafkaIsDown() {
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .when(kafkaTemplate).send(anyString(), anyString(), any());
    }

    private PaymentOutbox onlyOutbox() {
        List<PaymentOutbox> saved = paymentOutboxRepository.findAll();
        assertThat(saved).hasSize(1);
        return saved.getFirst();
    }

    // ===== 아웃박스 기록 =====

    @Test
    @DisplayName("예치금으로 결제가 끝나면 결제 완료 이벤트가 주문 그룹 ID 키로 아웃박스에 저장된다")
    void requestPayment_savesPaymentPaidOutbox() {
        givenBalance(TOTAL);

        paymentFacade.requestPayment(twoOrders());

        Payment payment = paymentRepository.findByOrderGroupId(ORDER_GROUP_ID).orElseThrow();
        PaymentOutbox outbox = onlyOutbox();
        assertThat(outbox.getTopic()).isEqualTo("payment.payment-paid");
        assertThat(outbox.getMessageKey()).isEqualTo(String.valueOf(ORDER_GROUP_ID));
        assertThat(outbox.getPublishedAt()).isNull();

        PaymentPaidEvent event = objectMapper.readValue(outbox.getPayload(), PaymentPaidEvent.class);
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.orderGroupId()).isEqualTo(ORDER_GROUP_ID);
        assertThat(event.paymentId()).isEqualTo(payment.getId());
        assertThat(event.buyerId()).isEqualTo(BUYER_ID);
        assertThat(event.totalPrice()).isEqualTo(TOTAL);
        assertThat(event.paidAt()).isNotNull();
    }

    @Test
    @DisplayName("결제창이 필요한 결제 요청은 아직 결제가 끝나지 않았으므로 아웃박스에 아무 것도 남기지 않는다")
    void requestPayment_needsCharge_savesNothing() {
        paymentFacade.requestPayment(twoOrders());      // 예치금 0 → 부족분 충전 필요

        assertThat(paymentOutboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("주문을 환불하면 환불 완료 이벤트가 주문 ID 키로 아웃박스에 저장된다")
    void refund_savesWalletRefundedOutbox() {
        givenPaidOrders();

        walletFacade.refund(9L, ORDER_A, 33_000);

        PaymentOutbox outbox = onlyOutbox();
        assertThat(outbox.getTopic()).isEqualTo("payment.payment-refund");
        assertThat(outbox.getMessageKey()).isEqualTo(String.valueOf(ORDER_A));
        assertThat(outbox.getPublishedAt()).isNull();

        WalletRefundedEvent event = objectMapper.readValue(outbox.getPayload(), WalletRefundedEvent.class);
        assertThat(event.refundId()).isEqualTo(9L);
        assertThat(event.orderId()).isEqualTo(ORDER_A);
        assertThat(event.amount()).isEqualTo(33_000);
        assertThat(event.refundedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 환불된 주문의 환불이 다시 오면 돈은 움직이지 않고 환불 완료 이벤트만 아웃박스에 한 번 더 저장된다")
    void refund_again_savesOutboxAgain() {
        givenPaidOrders();
        walletFacade.refund(9L, ORDER_A, 33_000);
        long balanceAfterRefund = walletRepository.findByHolderId(BUYER_ID).orElseThrow().getBalance();

        walletFacade.refund(9L, ORDER_A, 33_000);

        assertThat(walletRepository.findByHolderId(BUYER_ID).orElseThrow().getBalance()).isEqualTo(balanceAfterRefund);
        assertThat(paymentOutboxRepository.findAll())
                .hasSize(2)
                .allSatisfy(outbox -> assertThat(outbox.getTopic()).isEqualTo("payment.payment-refund"));
    }

    @Test
    @DisplayName("구매 확정된 주문의 정산금을 지급하면 정산 지급 완료 이벤트가 주문 ID 키로 아웃박스에 저장된다")
    void releaseHolding_savesWalletReleasedOutbox() {
        givenPaidOrders();
        walletFacade.confirmHolding(ORDER_B);

        walletFacade.releaseHolding(ORDER_B);

        PaymentOutbox outbox = onlyOutbox();
        assertThat(outbox.getTopic()).isEqualTo("payment.payment-released");
        assertThat(outbox.getMessageKey()).isEqualTo(String.valueOf(ORDER_B));
        assertThat(outbox.getPublishedAt()).isNull();

        WalletReleasedEvent event = objectMapper.readValue(outbox.getPayload(), WalletReleasedEvent.class);
        assertThat(event.orderId()).isEqualTo(ORDER_B);
        assertThat(event.sellerId()).isEqualTo(SELLER_ID);
        assertThat(event.amount()).isEqualTo(17_000);
        assertThat(event.settlementAmount() + event.feeAmount()).isEqualTo(17_000);
        assertThat(event.releasedAt()).isNotNull();
    }

    @Test
    @DisplayName("이벤트를 발행한 트랜잭션이 롤백되면 아웃박스에 남지 않는다")
    void rolledBack_doesNotSaveOutbox() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(PaymentPaidEvent.of(ORDER_GROUP_ID, 7L, BUYER_ID, TOTAL, LocalDateTime.now()));
            status.setRollbackOnly();
        });

        assertThat(paymentOutboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("이벤트를 발행한 뒤 커밋 직전에 실패해 롤백되면 아웃박스에 남지 않는다")
    void failedBeforeCommit_doesNotSaveOutbox() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(PaymentPaidEvent.of(ORDER_GROUP_ID, 7L, BUYER_ID, TOTAL, LocalDateTime.now()));
            TransactionSynchronizationManager.registerSynchronization(new FailBeforeCommit());
        })).isInstanceOf(IllegalStateException.class);

        assertThat(paymentOutboxRepository.count()).isZero();
    }

    // ===== 아웃박스 전송 =====

    @Test
    @DisplayName("스케줄러는 아웃박스의 토픽과 키로 저장된 JSON을 보내고, 보낸 뒤 발행 시각을 기록한다")
    void relay_sendsStoredJsonAndMarksPublished() {
        kafkaAcceptsEverything();
        givenBalance(TOTAL);
        paymentFacade.requestPayment(twoOrders());
        PaymentOutbox saved = onlyOutbox();

        paymentOutboxScheduler.sendOutbox();

        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("payment.payment-paid"), eq(String.valueOf(ORDER_GROUP_ID)), value.capture());
        assertThat(value.getValue()).isInstanceOf(JsonNode.class);
        assertThat(value.getValue()).isEqualTo(objectMapper.readTree(saved.getPayload()));
        assertThat(onlyOutbox().getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 발행한 아웃박스는 다음 주기에 다시 보내지 않는다")
    void relay_doesNotResendPublished() {
        kafkaAcceptsEverything();
        givenBalance(TOTAL);
        paymentFacade.requestPayment(twoOrders());
        paymentOutboxScheduler.sendOutbox();

        paymentOutboxScheduler.sendOutbox();

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Kafka 전송이 실패하면 그 아웃박스는 발행으로 표시하지 않고 그 회차에는 다음 아웃박스를 보내지 않으며, 다음 주기에 순서대로 다시 보낸다")
    void relay_sendFails_keepsUnpublishedThenRetriesInOrder() {
        givenPaidOrders();
        walletFacade.refund(9L, ORDER_A, 33_000);
        walletFacade.confirmHolding(ORDER_B);
        walletFacade.releaseHolding(ORDER_B);

        kafkaIsDown();
        paymentOutboxScheduler.sendOutbox();

        verify(kafkaTemplate).send(eq("payment.payment-refund"), eq(String.valueOf(ORDER_A)), any());
        verify(kafkaTemplate, never()).send(eq("payment.payment-released"), anyString(), any());
        assertThat(paymentOutboxRepository.findAll())
                .hasSize(2)
                .allSatisfy(outbox -> assertThat(outbox.getPublishedAt()).isNull());

        kafkaAcceptsEverything();
        paymentOutboxScheduler.sendOutbox();

        verify(kafkaTemplate, times(2)).send(eq("payment.payment-refund"), eq(String.valueOf(ORDER_A)), any());
        verify(kafkaTemplate).send(eq("payment.payment-released"), eq(String.valueOf(ORDER_B)), any());
        assertThat(paymentOutboxRepository.findAll())
                .hasSize(2)
                .allSatisfy(outbox -> assertThat(outbox.getPublishedAt()).isNotNull());
    }

    @Test
    @DisplayName("아웃박스를 거쳐 보내는 Kafka 메시지 본문은 이벤트 객체를 바로 보낼 때와 같다")
    void relay_messageBodyIsSameAsSendingEventDirectly() {
        kafkaAcceptsEverything();
        LocalDateTime at = LocalDateTime.of(2026, 11, 19, 12, 0, 0, 123_456_000);
        List<Object> events = List.of(
                PaymentPaidEvent.of(ORDER_GROUP_ID, 7L, BUYER_ID, TOTAL, at),
                new WalletRefundedEvent(9L, ORDER_A, 7L, 33_000L, at),
                new WalletReleasedEvent(ORDER_B, SELLER_ID, 17_000L, 1_700L, 15_300L, at));
        transactionTemplate.executeWithoutResult(status -> events.forEach(eventPublisher::publish));

        paymentOutboxScheduler.sendOutbox();

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), sent.capture());
        try (JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>()) {
            for (int i = 0; i < events.size(); i++) {
                String viaOutbox = new String(serializer.serialize("topic", sent.getAllValues().get(i)), StandardCharsets.UTF_8);
                String direct = new String(serializer.serialize("topic", events.get(i)), StandardCharsets.UTF_8);
                assertThat(viaOutbox).isEqualTo(direct);
            }
        }
    }

    static class FailBeforeCommit implements TransactionSynchronization {

        @Override
        public void beforeCommit(boolean readOnly) {
            throw new IllegalStateException("커밋 직전 실패");
        }
    }
}
