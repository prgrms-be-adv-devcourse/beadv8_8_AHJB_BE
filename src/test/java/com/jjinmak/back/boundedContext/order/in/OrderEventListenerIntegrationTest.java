package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.back.shared.payment.event.PaymentPaidEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.DELIVERY_ERROR;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.PAID;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPING;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.AT_PICKUP;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.EXCEPTION;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.OUT_FOR_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@RecordApplicationEvents
class OrderEventListenerIntegrationTest {

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
    CartItemRepository cartItemRepository;

    @Autowired
    ApplicationEvents events;

    UUID winnerUuid = UUID.randomUUID();
    UUID sellerUuid = UUID.randomUUID();
    UUID otherSellerUuid = UUID.randomUUID();

    LocalDateTime winAt = LocalDateTime.now();

    LocalDateTime paidAt = LocalDateTime.of(2026, 10, 19, 12, 0);

    @AfterEach
    void tearDown() {
        orderRepository.deleteAllInBatch();
        orderGroupRepository.deleteAllInBatch();
        cartItemRepository.deleteAllInBatch();
        orderMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("트랜잭션 안에서 낙찰 이벤트를 발행하면, 커밋 뒤 주문 회원을 낙찰자·판매자로 가진 장바구니 항목이 생긴다")
    void auctionWon_insideTransaction_createsItem() {
        syncMembers();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(wonEvent(10L)));

        CartItem item = cartItemRepository.findByAuctionId(10L).orElseThrow();
        assertThat(item.getWinner().getId()).isEqualTo(1L);
        assertThat(item.getSeller().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행한 낙찰 이벤트는 리스너가 받지 않는다")
    void auctionWon_outsideTransaction_ignored() {
        syncMembers();

        eventPublisher.publish(wonEvent(10L));

        assertThat(cartItemRepository.findByAuctionId(10L)).isEmpty();
    }

    @Test
    @DisplayName("같은 낙찰 이벤트가 두 번 와도 장바구니 항목은 1개다")
    void auctionWon_twice_onlyOneItem() {
        syncMembers();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(wonEvent(10L)));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(wonEvent(10L)));

        assertThat(cartItemRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원 동기화보다 낙찰 이벤트가 먼저 오면 장바구니 항목이 생기지 않는다 (현재 동작)")
    void auctionWon_beforeMemberSync_noItem() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(wonEvent(10L)));

        assertThat(cartItemRepository.findByAuctionId(10L)).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 안에서 배송 생성 이벤트를 발행하면, 커밋 뒤 결제 완료 주문이 SHIPPING이 된다")
    void deliveryCreated_insideTransaction_paidOrderBecomesShipping() {
        Long orderId = saveOrder(PAID);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(new DeliveryCreatedEvent(orderId)));

        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(SHIPPING);
    }

    @Test
    @DisplayName("배송 완료 이벤트를 받으면 배송 중인 주문이 SHIPPED가 되고, 구매 확정 기한은 배송 완료 시각 + 7일이다")
    void deliveryStatusUpdated_delivered_shippingOrderBecomesShipped() {
        Long orderId = saveOrder(SHIPPING);
        LocalDateTime updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(
                orderId, OUT_FOR_DELIVERY, DELIVERED, updatedAt);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isEqualTo(updatedAt.plusDays(7));
    }

    @Test
    @DisplayName("배송 완료가 아닌 상태로 바뀌었다는 이벤트를 받으면 주문은 SHIPPING 그대로다")
    void deliveryStatusUpdated_inTransit_orderStaysShipping() {
        Long orderId = saveOrder(SHIPPING);
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(
                orderId, AT_PICKUP, IN_TRANSIT, LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(SHIPPING);
        assertThat(order.getConfirmExpiredAt()).isNull();
    }

    @Test
    @DisplayName("배송 문제(EXCEPTION) 이벤트를 받으면 배송 중인 주문이 DELIVERY_ERROR가 된다")
    void deliveryStatusUpdated_exception_shippingOrderBecomesDeliveryError() {
        Long orderId = saveOrder(SHIPPING);
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(
                orderId, IN_TRANSIT, EXCEPTION, LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(DELIVERY_ERROR);
        assertThat(order.getConfirmExpiredAt()).isNull();
    }

    @Test
    @DisplayName("배송 오류 주문의 배송이 EXCEPTION에서 바로 끝났다는 이벤트를 받으면 SHIPPED가 되고, 구매 확정 기한은 배송 완료 시각 + 7일이다")
    void deliveryStatusUpdated_deliveredRightAfterException_deliveryErrorOrderBecomesShipped() {
        Long orderId = saveOrder(DELIVERY_ERROR);
        LocalDateTime updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(
                orderId, EXCEPTION, DELIVERED, updatedAt);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isEqualTo(updatedAt.plusDays(7));
    }

    @Test
    @DisplayName("운송장을 바꿔 이전 배송 상태가 WAITING인 상태 변경 이벤트를 받아도 배송 오류 주문은 SHIPPING으로 돌아간다")
    void deliveryStatusUpdated_afterWaybillChange_deliveryErrorOrderBecomesShipping() {
        Long orderId = saveOrder(DELIVERY_ERROR);
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(
                orderId, DeliveryStatusCode.WAITING, AT_PICKUP, LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(SHIPPING);
    }

    @Test
    @DisplayName("트랜잭션 안에서 결제 완료 이벤트를 발행하면, 커밋 뒤 그룹의 주문이 모두 결제 시각과 함께 PAID가 되고, 장바구니 항목이 지워지며 주문마다 결제 성공 이벤트가 발행된다")
    void paymentPaid_insideTransaction_completesOrders() {
        Long groupId = saveWaitingGroupWithCartItems();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(paidEvent(groupId, paidAt)));

        assertThat(orderRepository.findAll())
                .extracting(Order::getAuctionId, Order::getState, Order::getPaidAt)
                .containsExactlyInAnyOrder(tuple(10L, PAID, paidAt), tuple(11L, PAID, paidAt));
        assertThat(cartItemRepository.count()).isZero();
        assertThat(events.stream(OrderPaymentSucceedEvent.class))
                .extracting(OrderPaymentSucceedEvent::auctionId, OrderPaymentSucceedEvent::productId, OrderPaymentSucceedEvent::paidAt)
                .containsExactlyInAnyOrder(tuple(10L, 20L, paidAt), tuple(11L, 21L, paidAt));
    }

    @Test
    @DisplayName("결제 완료 이벤트를 발행한 트랜잭션이 롤백되면 주문은 WAITING, 장바구니 항목은 그대로이고 결제 성공 이벤트를 발행하지 않는다")
    void paymentPaid_rolledBack_ignored() {
        Long groupId = saveWaitingGroupWithCartItems();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(paidEvent(groupId, paidAt));
            status.setRollbackOnly();
        });

        assertThat(orderRepository.findAll())
                .extracting(Order::getState, Order::getPaidAt)
                .containsExactly(tuple(WAITING, null), tuple(WAITING, null));
        assertThat(cartItemRepository.count()).isEqualTo(2);
        assertThat(events.stream(OrderPaymentSucceedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("같은 그룹의 결제 완료 이벤트가 다시 와도 처음 결제 시각이 그대로이고, 결제 성공 이벤트는 주문마다 한 번만 발행된다")
    void paymentPaid_twice_firstPaymentKept() {
        Long groupId = saveWaitingGroupWithCartItems();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(paidEvent(groupId, paidAt)));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(paidEvent(groupId, paidAt.plusMinutes(5))));

        assertThat(orderRepository.findAll())
                .extracting(Order::getState, Order::getPaidAt)
                .containsExactly(tuple(PAID, paidAt), tuple(PAID, paidAt));
        assertThat(events.stream(OrderPaymentSucceedEvent.class)).hasSize(2);
    }

    private void syncMembers() {
        orderMemberRepository.save(new OrderMember(1L, winnerUuid, "낙찰자"));
        orderMemberRepository.save(new OrderMember(2L, sellerUuid, "판매자"));
        orderMemberRepository.save(new OrderMember(4L, otherSellerUuid, "다른 판매자"));
    }

    private AuctionWonEvent wonEvent(Long auctionId) {
        return new AuctionWonEvent(auctionId, 20L, 1L, 2L, 30_000L, 3_000L, winAt);
    }

    private Long saveOrder(OrderState state) {
        OrderMember winner = orderMemberRepository.save(new OrderMember(1L, winnerUuid, "낙찰자"));
        OrderMember seller = orderMemberRepository.save(new OrderMember(2L, sellerUuid, "판매자"));
        OrderGroup group = orderGroupRepository.save(new OrderGroup(33_000L));
        Order order = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return orderRepository.save(order).getId();
    }

    private Long saveWaitingGroupWithCartItems() {
        OrderMember winner = orderMemberRepository.save(new OrderMember(1L, winnerUuid, "낙찰자"));
        OrderMember seller = orderMemberRepository.save(new OrderMember(2L, sellerUuid, "판매자"));
        cartItemRepository.save(new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, winAt));
        cartItemRepository.save(new CartItem(winner, seller, 11L, 21L, 50_000L, 4_000L, winAt));
        OrderGroup group = orderGroupRepository.save(new OrderGroup(87_000L));
        orderRepository.save(new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L));
        orderRepository.save(new Order(group, winner, seller, 11L, 21L, 50_000L, 4_000L));
        return group.getId();
    }

    private PaymentPaidEvent paidEvent(Long groupId, LocalDateTime paidAt) {
        return PaymentPaidEvent.of(groupId, 7L, 1L, 87_000L, paidAt);
    }
}
