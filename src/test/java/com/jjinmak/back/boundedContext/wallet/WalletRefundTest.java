package com.jjinmak.back.boundedContext.wallet;

import com.jjinmak.back.boundedContext.wallet.app.WalletFacade;
import com.jjinmak.back.boundedContext.wallet.domain.Holding;
import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import com.jjinmak.back.boundedContext.wallet.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.wallet.domain.enums.HoldingStatus;
import com.jjinmak.back.boundedContext.wallet.domain.enums.ReferenceType;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import com.jjinmak.back.boundedContext.wallet.out.HoldingRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletTransactionRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.ErrorCode;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import com.jjinmak.back.shared.refund.domain.RefundStatus;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import com.jjinmak.back.shared.refund.event.RefundAcceptedEvent;
import com.jjinmak.back.shared.wallet.dto.HoldDto;
import com.jjinmak.back.shared.wallet.event.WalletRefundedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.jjinmak.back.boundedContext.wallet.exception.WalletErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 환불 (WalletFacade.refund / RefundAcceptedEvent 수신) 검증.
 *
 * 구매자가 30,000원 충전 후 주문 A(12,000), B(6,000)를 홀딩한 상태에서 시작한다.
 * 주문 A만 환불하고, B는 HELD 로 남아 있어야 한다.
 */
@SpringBootTest
@RecordApplicationEvents
class WalletRefundTest {

    private static final long CHARGE_AMOUNT = 30_000L;
    private static final long ORDER_A_AMOUNT = 12_000L;   // 10,000 + 2,000
    private static final long ORDER_B_AMOUNT = 6_000L;    //  5,000 + 1,000
    private static final long HELD_TOTAL = ORDER_A_AMOUNT + ORDER_B_AMOUNT;

    @Autowired private WalletFacade walletFacade;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletMemberRepository walletMemberRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private EventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ApplicationEvents events;

    private Long buyerId, sellerId, paymentId, orderAId, orderBId, refundId;
    private Long buyerWalletId, sellerWalletId, escrowWalletId;
    private long escrowAfterHold;

    @BeforeEach
    void setUp() {
        buyerId   = randomId();
        sellerId  = randomId();
        paymentId = randomId();
        orderAId  = randomId();
        orderBId  = randomId();
        refundId  = randomId();

        buyerWalletId  = createUserWallet(buyerId);
        sellerWalletId = createUserWallet(sellerId);
        escrowWalletId = walletRepository.findByTypeAndHolderIsNull(WalletType.ESCROW).orElseThrow().getId();

        walletFacade.charge(new ChargeDto(buyerId, CHARGE_AMOUNT, paymentId));
        walletFacade.hold(new HoldDto(paymentId, buyerId, HELD_TOTAL, List.of(
                new HoldDto.Item(orderAId, sellerId, 10_000L, 2_000L),
                new HoldDto.Item(orderBId, sellerId,  5_000L, 1_000L))));
        escrowAfterHold = balanceOf(escrowWalletId);
    }

    @AfterEach
    void tearDown() {
        holdingRepository.deleteAll(holdingsOfPayment());
        walletTransactionRepository.deleteAll(transactionsOfThisTest());
        walletRepository.deleteById(buyerWalletId);
        walletRepository.deleteById(sellerWalletId);
        walletMemberRepository.deleteById(buyerId);
        walletMemberRepository.deleteById(sellerId);
    }

    // ===== 성공 =====

    @Test
    @DisplayName("환불 성공: ESCROW 감소, 구매자 증가, Holding REFUNDED, 다른 주문은 그대로 HELD")
    void refund_success() {
        walletFacade.refund(refundId, orderAId, ORDER_A_AMOUNT);

        assertThat(balanceOf(buyerWalletId)).isEqualTo(CHARGE_AMOUNT - HELD_TOTAL + ORDER_A_AMOUNT);
        assertThat(balanceOf(escrowWalletId)).isEqualTo(escrowAfterHold - ORDER_A_AMOUNT);

        Holding a = holdingOf(orderAId);
        assertThat(a.getStatus()).isEqualTo(HoldingStatus.REFUNDED);
        assertThat(a.getRefundedAt()).isNotNull();

        Holding b = holdingOf(orderBId);
        assertThat(b.getStatus()).isEqualTo(HoldingStatus.HELD);
        assertThat(b.getRefundedAt()).isNull();
    }

    @Test
    @DisplayName("원장: REFUND_PAY(ESCROW -) / REFUND_RECEIVE(구매자 +) 한 쌍, 같은 그룹, ORDER 참조, 합 0")
    void refund_ledger() {
        walletFacade.refund(refundId, orderAId, ORDER_A_AMOUNT);

        List<WalletTransaction> txs = transactionsOfThisTest().stream()
                .filter(tx -> tx.getType() == WalletTransactionType.REFUND_PAY
                        || tx.getType() == WalletTransactionType.REFUND_RECEIVE)
                .toList();

        assertThat(txs).hasSize(2);
        assertThat(txs).extracting(WalletTransaction::getTransactionGroupId).containsOnly(txs.get(0).getTransactionGroupId());
        assertThat(txs).extracting(WalletTransaction::getReferenceType).containsOnly(ReferenceType.ORDER);
        assertThat(txs).extracting(WalletTransaction::getReferenceId).containsOnly(orderAId);
        assertThat(txs.stream().mapToLong(WalletTransaction::getAmount).sum()).isZero();

        WalletTransaction pay = txs.stream().filter(t -> t.getType() == WalletTransactionType.REFUND_PAY).findFirst().orElseThrow();
        assertThat(pay.getWallet().getId()).isEqualTo(escrowWalletId);
        assertThat(pay.getAmount()).isEqualTo(-ORDER_A_AMOUNT);

        WalletTransaction receive = txs.stream().filter(t -> t.getType() == WalletTransactionType.REFUND_RECEIVE).findFirst().orElseThrow();
        assertThat(receive.getWallet().getId()).isEqualTo(buyerWalletId);
        assertThat(receive.getAmount()).isEqualTo(ORDER_A_AMOUNT);
        assertThat(receive.getBalanceAfter()).isEqualTo(CHARGE_AMOUNT - HELD_TOTAL + ORDER_A_AMOUNT);
    }

    @Test
    @DisplayName("환불 후 WalletRefundedEvent 가 refundId · orderId · paymentId · amount 를 담아 발행된다")
    void refund_publishesEvent() {
        walletFacade.refund(refundId, orderAId, ORDER_A_AMOUNT);

        List<WalletRefundedEvent> published = events.stream(WalletRefundedEvent.class).toList();
        assertThat(published).hasSize(1);

        WalletRefundedEvent e = published.get(0);
        assertThat(e.refundId()).isEqualTo(refundId);
        assertThat(e.orderId()).isEqualTo(orderAId);
        assertThat(e.paymentId()).isEqualTo(paymentId);
        assertThat(e.amount()).isEqualTo(ORDER_A_AMOUNT);
        assertThat(e.refundedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 주문에 환불을 두 번 호출해도 한 번만 반영되고 이벤트도 한 번만 나간다 (멱등)")
    void refund_isIdempotent() {
        walletFacade.refund(refundId, orderAId, ORDER_A_AMOUNT);
        walletFacade.refund(refundId, orderAId, ORDER_A_AMOUNT);

        assertThat(balanceOf(buyerWalletId)).isEqualTo(CHARGE_AMOUNT - HELD_TOTAL + ORDER_A_AMOUNT);
        assertThat(transactionsOfThisTest())
                .filteredOn(tx -> tx.getType() == WalletTransactionType.REFUND_RECEIVE)
                .hasSize(1);
        assertThat(events.stream(WalletRefundedEvent.class).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("RefundAcceptedEvent 를 받으면 리스너를 통해 환불이 수행된다")
    void refundAcceptedEvent_triggersRefund() {
        RefundDto dto = new RefundDto(refundId, orderAId, buyerId, RefundStatus.ACCEPTED, ORDER_A_AMOUNT,
                LocalDateTime.now().minusHours(1), LocalDateTime.now());

        // 리스너가 AFTER_COMMIT 이라 실제로 커밋되는 트랜잭션 안에서 발행해야 한다
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publish(new RefundAcceptedEvent(dto)));

        assertThat(holdingOf(orderAId).getStatus()).isEqualTo(HoldingStatus.REFUNDED);
        assertThat(balanceOf(buyerWalletId)).isEqualTo(CHARGE_AMOUNT - HELD_TOTAL + ORDER_A_AMOUNT);
    }

    // ===== 실패 =====

    @Test
    @DisplayName("Holding 이 없는 주문이면 HOLDING_NOT_FOUND")
    void refund_unknownOrder_fails() {
        assertFails(() -> walletFacade.refund(refundId, randomId(), ORDER_A_AMOUNT), HOLDING_NOT_FOUND);
        assertNothingRefunded();
    }

    @Test
    @DisplayName("요청 금액이 Holding 금액과 다르면 REFUND_AMOUNT_MISMATCH")
    void refund_amountMismatch_fails() {
        assertFails(() -> walletFacade.refund(refundId, orderAId, ORDER_A_AMOUNT - 1), REFUND_AMOUNT_MISMATCH);
        assertNothingRefunded();
    }

    @Test
    @DisplayName("이미 정산(RELEASED)된 Holding 에 환불하면 HOLDING_ALREADY_RELEASED 이고 조용히 넘어가지 않는다")
    void refund_releasedHolding_fails() {
        // 정산 유스케이스가 아직 없어 상태만 직접 바꾼다
        transactionTemplate.executeWithoutResult(status ->
                holdingRepository.findByOrderId(orderAId).orElseThrow().release());

        assertFails(() -> walletFacade.refund(refundId, orderAId, ORDER_A_AMOUNT), HOLDING_ALREADY_RELEASED);

        assertThat(holdingOf(orderAId).getStatus()).isEqualTo(HoldingStatus.RELEASED);
        assertThat(balanceOf(buyerWalletId)).isEqualTo(CHARGE_AMOUNT - HELD_TOTAL);
        assertThat(balanceOf(escrowWalletId)).isEqualTo(escrowAfterHold);
    }

    // ===== helpers =====

    private void assertNothingRefunded() {
        assertThat(balanceOf(buyerWalletId)).isEqualTo(CHARGE_AMOUNT - HELD_TOTAL);
        assertThat(balanceOf(escrowWalletId)).isEqualTo(escrowAfterHold);
        assertThat(holdingOf(orderAId).getStatus()).isEqualTo(HoldingStatus.HELD);
        assertThat(holdingOf(orderBId).getStatus()).isEqualTo(HoldingStatus.HELD);
        assertThat(transactionsOfThisTest())
                .extracting(WalletTransaction::getType)
                .doesNotContain(WalletTransactionType.REFUND_PAY, WalletTransactionType.REFUND_RECEIVE);
        assertThat(events.stream(WalletRefundedEvent.class).count()).isZero();
    }

    private void assertFails(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private Long createUserWallet(Long memberId) {
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        return walletRepository.save(Wallet.createUserWallet(member)).getId();
    }

    private long balanceOf(Long walletId) {
        return walletRepository.findById(walletId).orElseThrow().getBalance();
    }

    private Holding holdingOf(Long orderId) {
        return holdingRepository.findByOrderId(orderId).orElseThrow();
    }

    private List<Holding> holdingsOfPayment() {
        return holdingRepository.findAll().stream()
                .filter(h -> paymentId.equals(h.getPaymentId()))
                .toList();
    }

    private List<WalletTransaction> transactionsOfThisTest() {
        Set<Long> myWallets = Set.of(buyerWalletId, sellerWalletId);
        Set<Long> myOrders  = Set.of(orderAId, orderBId);
        return walletTransactionRepository.findAll().stream()
                .filter(tx -> myWallets.contains(tx.getWallet().getId())
                        || (tx.getWallet().getId().equals(escrowWalletId) && myOrders.contains(tx.getReferenceId())))
                .toList();
    }

    private static Long randomId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }
}