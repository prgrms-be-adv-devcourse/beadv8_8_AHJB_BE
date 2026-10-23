package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.auction.in.AuctionEventListener;
import com.jjinmak.back.boundedContext.order.app.OrderFacade;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.boundedContext.payment.in.PaymentEventListener;
import com.jjinmak.back.boundedContext.refund.in.RefundEventListener;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import com.jjinmak.back.shared.refund.dto.RefundDto;
import com.jjinmak.back.shared.refund.event.RefundAcceptedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUNDED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUND_REQUESTED;
import static com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent.RefundType.SYSTEM_REQUEST;
import static com.jjinmak.back.shared.refund.domain.RefundStatus.ACCEPTED;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RecordApplicationEvents
class OrderSystemRefundIntegrationTest {

    @Autowired
    OrderFacade orderFacade;

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    OrderMemberRepository orderMemberRepository;

    @Autowired
    OrderGroupRepository orderGroupRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    ApplicationEvents events;

    @MockitoBean
    RefundEventListener refundEventListener;

    @MockitoBean
    PaymentEventListener paymentEventListener;

    @MockitoBean
    AuctionEventListener auctionEventListener;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAllInBatch();
        orderGroupRepository.deleteAllInBatch();
        orderMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("시스템 환불을 요청하면 결제 완료 주문이 REFUND_REQUESTED로 저장되고, 시스템이 요청한 환불 요청 이벤트를 한 번 발행한다")
    void requestSystemRefund_savesRefundRequestedAndPublishes() {
        Long orderId = savePaidOrder();

        orderFacade.requestSystemRefund(orderId);

        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(REFUND_REQUESTED);
        assertThat(events.stream(OrderRefundRequestedEvent.class))
                .containsExactly(new OrderRefundRequestedEvent(orderId, 1L, 2L, 20L, 30_000L, 3_000L, SYSTEM_REQUEST));
    }

    @Test
    @DisplayName("시스템 환불을 요청한 주문에 대해 트랜잭션 안에서 환불 승인 이벤트를 발행하면, 커밋 뒤 주문이 REFUNDED가 된다")
    void requestSystemRefund_thenRefundAccepted_becomesRefunded() {
        Long orderId = savePaidOrder();
        orderFacade.requestSystemRefund(orderId);

        RefundDto refund = new RefundDto(7L, orderId, 1L, ACCEPTED, 33_000L, LocalDateTime.now(), LocalDateTime.now());
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new RefundAcceptedEvent(refund)));

        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(REFUNDED);
    }

    private Long savePaidOrder() {
        OrderMember winner = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));
        OrderMember seller = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "판매자"));
        OrderGroup group = orderGroupRepository.save(new OrderGroup(33_000L));
        Order order = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.paid(LocalDateTime.now());
        return orderRepository.save(order).getId();
    }
}
