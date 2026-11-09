package com.jjinmak.order.in;

import com.jjinmak.contracts.order.event.OrderRefundRequestedEvent;
import com.jjinmak.contracts.refund.dto.RefundDto;
import com.jjinmak.contracts.refund.event.RefundAcceptedEvent;
import com.jjinmak.order.app.OrderFacade;
import com.jjinmak.order.domain.Order;
import com.jjinmak.order.domain.OrderGroup;
import com.jjinmak.order.domain.OrderMember;
import com.jjinmak.order.out.repository.OrderGroupRepository;
import com.jjinmak.order.out.repository.OrderMemberRepository;
import com.jjinmak.order.out.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.contracts.order.event.OrderRefundRequestedEvent.RefundType.SYSTEM_REQUEST;
import static com.jjinmak.contracts.refund.domain.RefundStatus.ACCEPTED;
import static com.jjinmak.order.domain.OrderState.REFUNDED;
import static com.jjinmak.order.domain.OrderState.REFUND_REQUESTED;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RecordApplicationEvents
class OrderSystemRefundIntegrationTest {

    @Autowired
    OrderFacade orderFacade;

    @Autowired
    OrderKafkaListener orderKafkaListener;

    @Autowired
    OrderMemberRepository orderMemberRepository;

    @Autowired
    OrderGroupRepository orderGroupRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    ApplicationEvents events;

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
    @DisplayName("시스템 환불을 요청한 주문에 대해 환불 승인 이벤트를 받으면 주문이 REFUNDED가 된다")
    void requestSystemRefund_thenRefundAccepted_becomesRefunded() {
        Long orderId = savePaidOrder();
        orderFacade.requestSystemRefund(orderId);

        RefundDto refund = new RefundDto(7L, orderId, 1L, ACCEPTED, 33_000L, LocalDateTime.now(), LocalDateTime.now());
        orderKafkaListener.handle(RefundAcceptedEvent.of(refund));

        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(REFUNDED);
    }

    private Long savePaidOrder() {
        OrderMember winner = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));
        OrderMember seller = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "판매자"));
        OrderGroup group = orderGroupRepository.save(new OrderGroup(winner, 33_000L));
        Order order = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.paid(LocalDateTime.now());
        return orderRepository.save(order).getId();
    }
}
