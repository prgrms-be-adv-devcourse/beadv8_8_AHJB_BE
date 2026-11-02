package com.jjinmak.back.boundedContext.payment.app;

import com.jjinmak.back.boundedContext.payment.domain.Holding;
import com.jjinmak.back.boundedContext.payment.domain.Payment;
import com.jjinmak.back.boundedContext.payment.domain.Wallet;
import com.jjinmak.back.boundedContext.payment.domain.WalletMember;
import com.jjinmak.back.boundedContext.payment.domain.enums.HoldingStatus;
import com.jjinmak.back.boundedContext.payment.domain.enums.WalletTransactionType;
import com.jjinmak.back.boundedContext.payment.out.HoldingRepository;
import com.jjinmak.back.boundedContext.payment.out.PaymentRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletMemberRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletRepository;
import com.jjinmak.back.boundedContext.payment.out.WalletTransactionRepository;
import com.jjinmak.back.boundedContext.refund.in.RefundEventListener;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.shared.payment.event.WalletRefundedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 환불 재요청(RefundRetryRequestedEvent) 처리.
 * 리스너는 환불 수락 이벤트와 같은 WalletFacade.refund 를 부르므로, 그 진입점으로 재요청 시나리오를 검증한다.
 *
 * 시나리오: 구매자가 주문 81 을 10_000 에 결제 → ESCROW 에 10_000 홀딩(HELD). 환불 id 501.
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class RefundRetryTest {

    private static final Long BUYER_ID = 9_701L;
    private static final Long SELLER_ID = 9_702L;
    private static final Long ORDER_ID = 81L;
    private static final Long REFUND_ID = 501L;
    private static final long AMOUNT = 10_000L;

    @Autowired WalletFacade walletFacade;
    @Autowired SystemWalletFinder finder;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired HoldingRepository holdingRepository;
    @Autowired WalletTransactionRepository walletTransactionRepository;
    @Autowired ApplicationEvents events;

    @MockitoBean SettlementEventListener settlementEventListener;
    @MockitoBean RefundEventListener refundEventListener;   // 같은 트랜잭션 @EventListener 라 실제 Refund 행이 없으면 REFUND_NOT_FOUND

    private Wallet buyerWallet;
    private Payment payment;
    private Holding holding;
    private long escrowBefore;

    @BeforeEach
    void setUp() {
        buyerWallet = walletOf(BUYER_ID);
        Wallet sellerWallet = walletOf(SELLER_ID);

        Wallet escrow = finder.escrow();
        escrow.deposit(AMOUNT);                                   // 결제 때 ESCROW 로 들어온 돈
        escrowBefore = escrow.getBalance();

        payment = Payment.create(8_801L, BUYER_ID, buyerWallet, AMOUNT);
        payment.addItem(ORDER_ID, SELLER_ID, AMOUNT, 0L);
        payment.validateItems();
        payment.markPaid(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        holding = holdingRepository.save(
                Holding.hold(ORDER_ID, payment.getId(), buyerWallet, sellerWallet, AMOUNT, 0L, 1_000L));
    }

    private Wallet walletOf(Long memberId) {
        WalletMember member = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
        return walletRepository.save(Wallet.createUserWallet(member));
    }

    private List<WalletRefundedEvent> refundedEvents() {
        return events.stream(WalletRefundedEvent.class).toList();
    }

    private long refundLedgerCount() {
        return walletTransactionRepository.findByWalletAndType(buyerWallet, WalletTransactionType.REFUND_RECEIVE,
                PageRequest.of(0, 10)).getTotalElements();
    }

    @Test
    @DisplayName("첫 처리가 안 된 홀딩(HELD)에 재요청 → 환불 처리 + 회신 발행 (환불 수락 이벤트와 동일)")
    void retry_onHeldHolding_refunds() {
        walletFacade.refund(REFUND_ID, ORDER_ID, AMOUNT);

        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.REFUNDED);
        assertThat(buyerWallet.getBalance()).isEqualTo(AMOUNT);
        assertThat(finder.escrow().getBalance()).isEqualTo(escrowBefore - AMOUNT);
        assertThat(payment.findItem(ORDER_ID).isRefunded()).isTrue();

        List<WalletRefundedEvent> published = refundedEvents();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().refundId()).isEqualTo(REFUND_ID);
        assertThat(published.getFirst().orderId()).isEqualTo(ORDER_ID);
        assertThat(published.getFirst().amount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("이미 환불된 홀딩에 재요청(회신 유실 케이스) → 돈은 안 움직이고 회신만 다시 발행")
    void retry_onRefundedHolding_republishesOnly() {
        walletFacade.refund(REFUND_ID, ORDER_ID, AMOUNT);          // 첫 처리
        long balanceAfterFirst = buyerWallet.getBalance();
        long escrowAfterFirst = finder.escrow().getBalance();
        LocalDateTime refundedAt = holding.getRefundedAt();

        walletFacade.refund(REFUND_ID, ORDER_ID, AMOUNT);          // 재요청 — 예외 없이 끝나야 한다

        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.REFUNDED);
        assertThat(holding.getRefundedAt()).isEqualTo(refundedAt);             // 환불 시각 덮어쓰지 않음
        assertThat(buyerWallet.getBalance()).isEqualTo(balanceAfterFirst);      // 이중 환불 없음
        assertThat(finder.escrow().getBalance()).isEqualTo(escrowAfterFirst);
        assertThat(refundLedgerCount()).isEqualTo(1);                           // 원장도 한 번만

        List<WalletRefundedEvent> published = refundedEvents();
        assertThat(published).hasSize(2);                                       // 첫 회신 + 재발행
        assertThat(published.getLast().refundId()).isEqualTo(REFUND_ID);
        assertThat(published.getLast().amount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("재요청이 여러 번 와도 결과는 같다 (멱등)")
    void retry_manyTimes_idempotent() {
        for (int i = 0; i < 3; i++) {
            walletFacade.refund(REFUND_ID, ORDER_ID, AMOUNT);
        }

        assertThat(buyerWallet.getBalance()).isEqualTo(AMOUNT);
        assertThat(finder.escrow().getBalance()).isEqualTo(escrowBefore - AMOUNT);
        assertThat(refundLedgerCount()).isEqualTo(1);
        assertThat(refundedEvents()).hasSize(3);                                // 요청마다 회신은 간다
    }

    @Test
    @DisplayName("구매 확정된 홀딩에 재요청 → 거절, 돈·회신 없음")
    void retry_onConfirmedHolding_rejected() {
        holding.confirm();

        assertThatThrownBy(() -> walletFacade.refund(REFUND_ID, ORDER_ID, AMOUNT))
                .isInstanceOf(BusinessException.class);

        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.CONFIRMED);
        assertThat(buyerWallet.getBalance()).isZero();
        assertThat(finder.escrow().getBalance()).isEqualTo(escrowBefore);
        assertThat(refundedEvents()).isEmpty();
    }

    @Test
    @DisplayName("요청 금액이 홀딩 금액과 다르면 거절, 돈·회신 없음")
    void retry_amountMismatch_rejected() {
        assertThatThrownBy(() -> walletFacade.refund(REFUND_ID, ORDER_ID, AMOUNT - 1))
                .isInstanceOf(BusinessException.class);

        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.HELD);
        assertThat(buyerWallet.getBalance()).isZero();
        assertThat(refundedEvents()).isEmpty();
    }
}
