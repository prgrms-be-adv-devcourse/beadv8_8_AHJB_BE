package com.jjinmak.refund.in;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.order.event.OrderRefundRequestedEvent;
import com.jjinmak.contracts.payment.event.WalletRefundedEvent;
import com.jjinmak.contracts.refund.event.RefundAcceptedEvent;
import com.jjinmak.refund.domain.Refund;
import com.jjinmak.refund.out.RefundRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;

import static com.jjinmak.contracts.refund.domain.RefundStatus.ACCEPTED;
import static com.jjinmak.contracts.refund.domain.RefundStatus.COMPLETED;
import static com.jjinmak.contracts.refund.domain.RefundStatus.REQUESTED;
import static com.jjinmak.refund.exception.RefundErrorCode.REFUND_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@RecordApplicationEvents
class RefundEventListenerIntegrationTest {

    static final Long ORDER_ID = 1L;
    static final Long WINNER_ID = 10L;
    static final Long SELLER_ID = 20L;
    static final Long PRODUCT_ID = 100L;
    static final Long WINNING_PRICE = 50_000L;
    static final Long DELIVERY_FEE = 3_000L;
    static final Long PAID_AMOUNT = 53_000L;

    @Autowired
    RefundEventListener refundEventListener;

    @Autowired
    RefundRepository refundRepository;

    @Autowired
    ApplicationEvents events;

    @AfterEach
    void tearDown() {
        refundRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("낙찰자 환불 요청을 받으면 결제 금액 전체(낙찰가 + 배송비)로 REQUESTED 환불이 생긴다")
    void winnerRequest_createsRequestedRefund() {
        refundEventListener.handle(winnerRequest());

        assertThat(refundRepository.findAll())
                .extracting(Refund::getOrderId, Refund::getWinnerId, Refund::getSellerId, Refund::getAmount, Refund::getStatus)
                .containsExactly(tuple(ORDER_ID, WINNER_ID, SELLER_ID, PAID_AMOUNT, REQUESTED));
        assertThat(events.stream(RefundAcceptedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("같은 환불 요청이 두 번 와도 환불은 한 건만 생긴다")
    void winnerRequest_duplicated_createsOnlyOne() {
        refundEventListener.handle(winnerRequest());
        refundEventListener.handle(winnerRequest());

        assertThat(refundRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("시스템 환불 요청을 받으면 판매자 판단 없이 수락하고 환불 수락 이벤트를 발행한다")
    void systemRequest_acceptsAndPublishes() {
        refundEventListener.handle(systemRequest());

        Refund refund = refundRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertThat(refund.getStatus()).isEqualTo(ACCEPTED);
        assertThat(refund.getProcessedAt()).isNotNull();
        assertThat(events.stream(RefundAcceptedEvent.class))
                .extracting(event -> event.refund().orderId(), event -> event.refund().amount(), event -> event.refund().status())
                .containsExactly(tuple(ORDER_ID, PAID_AMOUNT, ACCEPTED));
    }

    @Test
    @DisplayName("같은 시스템 환불 요청이 두 번 와도 환불과 수락 이벤트는 한 번뿐이다")
    void systemRequest_duplicated_publishesOnce() {
        refundEventListener.handle(systemRequest());
        refundEventListener.handle(systemRequest());

        assertThat(refundRepository.count()).isEqualTo(1);
        assertThat(events.stream(RefundAcceptedEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("지갑 환불 완료 이벤트를 받으면 수락된 환불이 COMPLETED가 된다")
    void walletRefunded_completesRefund() {
        Long refundId = acceptedRefundId();

        refundEventListener.handle(walletRefunded(refundId));

        assertThat(statusOf(refundId)).isEqualTo(COMPLETED);
    }

    @Test
    @DisplayName("같은 환불 완료 이벤트가 두 번 와도 예외 없이 COMPLETED로 남는다")
    void walletRefunded_duplicated_staysCompleted() {
        Long refundId = acceptedRefundId();

        refundEventListener.handle(walletRefunded(refundId));
        refundEventListener.handle(walletRefunded(refundId));

        assertThat(statusOf(refundId)).isEqualTo(COMPLETED);
    }

    @Test
    @DisplayName("없는 환불의 완료 이벤트는 REFUND_NOT_FOUND를 던진다 (삼키지 않아야 재시도·DLT로 간다)")
    void walletRefunded_unknownRefund_throws() {
        assertThatThrownBy(() -> refundEventListener.handle(walletRefunded(999L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(REFUND_NOT_FOUND);
    }

    private OrderRefundRequestedEvent winnerRequest() {
        return OrderRefundRequestedEvent.byWinner(ORDER_ID, WINNER_ID, SELLER_ID, PRODUCT_ID, WINNING_PRICE, DELIVERY_FEE);
    }

    private OrderRefundRequestedEvent systemRequest() {
        return OrderRefundRequestedEvent.bySystem(ORDER_ID, WINNER_ID, SELLER_ID, PRODUCT_ID, WINNING_PRICE, DELIVERY_FEE);
    }

    private WalletRefundedEvent walletRefunded(Long refundId) {
        return new WalletRefundedEvent(refundId, ORDER_ID, 7L, PAID_AMOUNT, LocalDateTime.of(2026, 11, 10, 12, 0));
    }

    // 시스템 환불 요청으로 ACCEPTED 상태의 환불을 만든다
    private Long acceptedRefundId() {
        refundEventListener.handle(systemRequest());
        return refundRepository.findByOrderId(ORDER_ID).orElseThrow().getId();
    }

    private com.jjinmak.contracts.refund.domain.RefundStatus statusOf(Long refundId) {
        return refundRepository.findById(refundId).orElseThrow().getStatus();
    }
}