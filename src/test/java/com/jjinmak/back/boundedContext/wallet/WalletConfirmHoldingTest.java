package com.jjinmak.back.boundedContext.wallet;

import com.jjinmak.back.boundedContext.wallet.app.WalletFacade;
import com.jjinmak.back.boundedContext.wallet.domain.Holding;
import com.jjinmak.back.boundedContext.wallet.domain.Wallet;
import com.jjinmak.back.boundedContext.wallet.domain.WalletMember;
import com.jjinmak.back.boundedContext.wallet.domain.WalletTransaction;
import com.jjinmak.back.boundedContext.wallet.domain.enums.HoldingStatus;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.wallet.domain.enums.WalletType;
import com.jjinmak.back.boundedContext.wallet.out.HoldingRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletRepository;
import com.jjinmak.back.boundedContext.wallet.out.WalletTransactionRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.exception.ErrorCode;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.payment.dto.ChargeDto;
import com.jjinmak.back.shared.wallet.dto.HoldDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * 구매 확정 (WalletFacade.confirmHolding / OrderConfirmPurchaseEvent 수신) 검증.
 *
 * 구매자가 20,000원 충전 후 주문 A(12,000), B(6,000)를 홀딩한 상태에서 시작한다.
 * 주문 A만 확정하고, B는 HELD 로 남아 있어야 한다. 확정은 돈을 움직이지 않는다.
 */
@SpringBootTest
class WalletConfirmHoldingTest {

    private static final long CHARGE_AMOUNT = 20_000L;
    private static final long ORDER_A_AMOUNT = 12_000L;
    private static final long ORDER_B_AMOUNT = 6_000L;
    private static final long HELD_TOTAL = ORDER_A_AMOUNT + ORDER_B_AMOUNT;

    @Autowired private WalletFacade walletFacade;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletMemberRepository walletMemberRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private EventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long buyerId, sellerId, paymentId, orderAId, orderBId;
    private Long buyerWalletId, sellerWalletId, escrowWalletId;
    private long escrowAfterHold;

    @BeforeEach
    void setUp() {
        buyerId   = randomId();
        sellerId  = randomId();
        paymentId = randomId();
        orderAId  = randomId();
        orderBId  = randomId();

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
    @DisplayName("구매 확정 시 Holding 이 CONFIRMED 가 되고 confirmedAt 이 기록된다. 다른 주문은 HELD 그대로")
    void confirm_success() {
        walletFacade.confirmHolding(orderAId);

        Holding a = holdingOf(orderAId);
        assertThat(a.getStatus()).isEqualTo(HoldingStatus.CONFIRMED);
        assertThat(a.getConfirmedAt()).isNotNull();

        Holding b = holdingOf(orderBId);
        assertThat(b.getStatus()).isEqualTo(HoldingStatus.HELD);
        assertThat(b.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("구매 확정은 돈을 움직이지 않는다. 잔액과 원장이 그대로다")
    void confirm_doesNotMoveMoney() {
        int txCountBefore = transactionsOfThisTest().size();

        walletFacade.confirmHolding(orderAId);

        assertThat(balanceOf(buyerWalletId)).isEqualTo(CHARGE_AMOUNT - HELD_TOTAL);
        assertThat(balanceOf(sellerWalletId)).isZero();
        assertThat(balanceOf(escrowWalletId)).isEqualTo(escrowAfterHold);
        assertThat(transactionsOfThisTest()).hasSize(txCountBefore);
    }

    @Test
    @DisplayName("같은 주문에 확정을 두 번 호출해도 한 번만 반영된다 (멱등)")
    void confirm_isIdempotent() {
        walletFacade.confirmHolding(orderAId);
        LocalDateTime firstConfirmedAt = holdingOf(orderAId).getConfirmedAt();

        walletFacade.confirmHolding(orderAId);

        Holding a = holdingOf(orderAId);
        assertThat(a.getStatus()).isEqualTo(HoldingStatus.CONFIRMED);
        assertThat(a.getConfirmedAt()).isEqualTo(firstConfirmedAt);   // 시각이 갱신되지 않는다
    }

    @Test
    @DisplayName("OrderConfirmPurchaseEvent 를 받으면 리스너를 통해 확정이 수행된다")
    void orderConfirmPurchaseEvent_triggersConfirm() {
        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(
                orderAId, buyerId, sellerId, randomId(), 10_000L, 2_000L, LocalDateTime.now());

        // 리스너가 AFTER_COMMIT 이라 실제로 커밋되는 트랜잭션 안에서 발행해야 한다
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertThat(holdingOf(orderAId).getStatus()).isEqualTo(HoldingStatus.CONFIRMED);
    }

    // ===== 실패 / 차단 =====

    @Test
    @DisplayName("Holding 이 없는 주문이면 HOLDING_NOT_FOUND")
    void confirm_unknownOrder_fails() {
        assertFails(() -> walletFacade.confirmHolding(randomId()), HOLDING_NOT_FOUND);
        assertThat(holdingOf(orderAId).getStatus()).isEqualTo(HoldingStatus.HELD);
    }

    @Test
    @DisplayName("확정된 Holding 에 환불을 시도하면 HOLDING_ALREADY_CONFIRMED 이고 돈이 움직이지 않는다")
    void refund_afterConfirm_isRejected() {
        walletFacade.confirmHolding(orderAId);

        assertFails(() -> walletFacade.refund(randomId(), orderAId, ORDER_A_AMOUNT), HOLDING_ALREADY_CONFIRMED);

        assertThat(holdingOf(orderAId).getStatus()).isEqualTo(HoldingStatus.CONFIRMED);
        assertThat(balanceOf(buyerWalletId)).isEqualTo(CHARGE_AMOUNT - HELD_TOTAL);
        assertThat(balanceOf(escrowWalletId)).isEqualTo(escrowAfterHold);
        assertThat(transactionsOfThisTest())
                .extracting(WalletTransaction::getType)
                .doesNotContain(WalletTransactionType.REFUND_PAY, WalletTransactionType.REFUND_RECEIVE);
    }

    @Test
    @DisplayName("이미 환불된 Holding 에 확정이 오면 HOLDING_ALREADY_REFUNDED (환불과 확정이 엇갈린 경우)")
    void confirm_afterRefund_fails() {
        walletFacade.refund(randomId(), orderAId, ORDER_A_AMOUNT);

        assertFails(() -> walletFacade.confirmHolding(orderAId), HOLDING_ALREADY_REFUNDED);
        assertThat(holdingOf(orderAId).getStatus()).isEqualTo(HoldingStatus.REFUNDED);
    }

    // ===== helpers =====

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

    private List<com.jjinmak.back.boundedContext.wallet.domain.WalletTransaction> transactionsOfThisTest() {
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