package com.jjinmak.order.in;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.delivery.domain.DeliveryStatusCode;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.contracts.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.order.domain.CartItem;
import com.jjinmak.order.domain.Order;
import com.jjinmak.order.domain.OrderGroup;
import com.jjinmak.order.domain.OrderMember;
import com.jjinmak.order.domain.OrderState;
import com.jjinmak.order.out.repository.CartItemRepository;
import com.jjinmak.order.out.repository.OrderGroupRepository;
import com.jjinmak.order.out.repository.OrderMemberRepository;
import com.jjinmak.order.out.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.jjinmak.common.exception.CommonErrorCode.USER_NOT_FOUND;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.AT_PICKUP;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.EXCEPTION;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.OUT_FOR_DELIVERY;
import static com.jjinmak.order.domain.OrderState.DELIVERY_ERROR;
import static com.jjinmak.order.domain.OrderState.PAID;
import static com.jjinmak.order.domain.OrderState.SHIPPED;
import static com.jjinmak.order.domain.OrderState.SHIPPING;
import static com.jjinmak.order.exception.OrderErrorCode.ORDER_STATE_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@RecordApplicationEvents
class OrderKafkaListenerIntegrationTest {

    @Autowired
    OrderKafkaListener orderKafkaListener;

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

    LocalDateTime paidAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @AfterEach
    void tearDown() {
        orderRepository.deleteAllInBatch();
        orderGroupRepository.deleteAllInBatch();
        cartItemRepository.deleteAllInBatch();
        orderMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("낙찰 이벤트를 받으면 주문 회원을 낙찰자·판매자로 가진 장바구니 항목이 생긴다")
    void auctionWon_createsItem() {
        syncMembers();

        orderKafkaListener.handle(wonEvent(10L));

        CartItem item = cartItemRepository.findByAuctionId(10L).orElseThrow();
        assertThat(item.getWinner().getId()).isEqualTo(1L);
        assertThat(item.getSeller().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("같은 낙찰 이벤트를 다시 받으면 두 번째 처리는 DataIntegrityViolationException으로 끝나고, 장바구니 항목은 1개다")
    void auctionWon_twice_secondFails() {
        syncMembers();
        orderKafkaListener.handle(wonEvent(10L));

        assertThatThrownBy(() -> orderKafkaListener.handle(wonEvent(10L)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(cartItemRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원 동기화보다 낙찰 이벤트를 먼저 받으면 USER_NOT_FOUND로 끝나고 장바구니 항목이 생기지 않는다")
    void auctionWon_beforeMemberSync_fails() {
        assertThatThrownBy(() -> orderKafkaListener.handle(wonEvent(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(USER_NOT_FOUND);

        assertThat(cartItemRepository.findByAuctionId(10L)).isEmpty();
    }

    @Test
    @DisplayName("배송 생성 이벤트를 받으면 결제 완료 주문이 SHIPPING이 된다")
    void deliveryCreated_paidOrderBecomesShipping() {
        Long orderId = saveOrder(PAID);

        orderKafkaListener.handle(new DeliveryCreatedEvent(orderId));

        assertThat(stateOf(orderId)).isEqualTo(SHIPPING);
    }

    @Test
    @DisplayName("배송 완료 이벤트를 받으면 배송 중인 주문이 SHIPPED가 되고, 구매 확정 기한은 배송 완료를 반영한 시각 + 7일이다")
    void deliveryStatusUpdated_delivered_shippingOrderBecomesShipped() {
        Long orderId = saveOrder(SHIPPING);
        DeliveryStatusUpdatedEvent event = statusEvent(orderId, OUT_FOR_DELIVERY, DELIVERED, LocalDateTime.now().minusDays(1));

        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        orderKafkaListener.handle(event);
        LocalDateTime after = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isBetween(before.plusDays(7), after.plusDays(7));
    }

    @Test
    @DisplayName("배송 완료가 아닌 상태로 바뀌었다는 이벤트를 받으면 주문은 SHIPPING 그대로다")
    void deliveryStatusUpdated_inTransit_orderStaysShipping() {
        Long orderId = saveOrder(SHIPPING);

        orderKafkaListener.handle(statusEvent(orderId, AT_PICKUP, IN_TRANSIT, LocalDateTime.now()));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(SHIPPING);
        assertThat(order.getConfirmExpiredAt()).isNull();
    }

    @Test
    @DisplayName("배송 문제(EXCEPTION) 이벤트를 받으면 배송 중인 주문이 DELIVERY_ERROR가 된다")
    void deliveryStatusUpdated_exception_shippingOrderBecomesDeliveryError() {
        Long orderId = saveOrder(SHIPPING);

        orderKafkaListener.handle(statusEvent(orderId, IN_TRANSIT, EXCEPTION, LocalDateTime.now()));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(DELIVERY_ERROR);
        assertThat(order.getConfirmExpiredAt()).isNull();
    }

    @Test
    @DisplayName("배송 오류 주문의 배송이 EXCEPTION에서 바로 끝났다는 이벤트를 받으면 SHIPPED가 되고, 구매 확정 기한은 배송 완료를 반영한 시각 + 7일이다")
    void deliveryStatusUpdated_deliveredRightAfterException_deliveryErrorOrderBecomesShipped() {
        Long orderId = saveOrder(DELIVERY_ERROR);
        DeliveryStatusUpdatedEvent event = statusEvent(orderId, EXCEPTION, DELIVERED, LocalDateTime.now().minusDays(1));

        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        orderKafkaListener.handle(event);
        LocalDateTime after = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isBetween(before.plusDays(7), after.plusDays(7));
    }

    @Test
    @DisplayName("운송장을 바꿔 이전 배송 상태가 WAITING인 상태 변경 이벤트를 받아도 배송 오류 주문은 SHIPPING으로 돌아간다")
    void deliveryStatusUpdated_afterWaybillChange_deliveryErrorOrderBecomesShipping() {
        Long orderId = saveOrder(DELIVERY_ERROR);

        orderKafkaListener.handle(statusEvent(orderId, DeliveryStatusCode.WAITING, AT_PICKUP, LocalDateTime.now()));

        assertThat(stateOf(orderId)).isEqualTo(SHIPPING);
    }

    @Test
    @DisplayName("배송 생성 이벤트보다 배송 상태 변경 이벤트를 먼저 받으면 ORDER_STATE_BAD_REQUEST로 끝나고 주문은 PAID 그대로이며, 배송 생성 이벤트를 받은 뒤 같은 상태 변경 이벤트를 다시 받으면 SHIPPED가 된다")
    void deliveryStatusUpdated_beforeDeliveryCreated_appliedWhenReceivedAgain() {
        Long orderId = saveOrder(PAID);
        DeliveryStatusUpdatedEvent delivered = statusEvent(orderId, OUT_FOR_DELIVERY, DELIVERED, LocalDateTime.now());

        assertThatThrownBy(() -> orderKafkaListener.handle(delivered))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);
        assertThat(stateOf(orderId)).isEqualTo(PAID);

        orderKafkaListener.handle(new DeliveryCreatedEvent(orderId));
        orderKafkaListener.handle(delivered);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(SHIPPED);
        assertThat(order.getConfirmExpiredAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 완료 이벤트를 받으면 그룹의 주문이 모두 결제 시각과 함께 PAID가 되고, 장바구니 항목이 지워지며 주문마다 그 주문의 낙찰자·판매자 id를 담은 결제 성공 이벤트가 발행된다")
    void paymentPaid_completesOrders() {
        Long groupId = saveWaitingGroupWithCartItems();

        orderKafkaListener.handle(paidEvent(groupId, paidAt));

        assertThat(orderRepository.findAll())
                .extracting(Order::getAuctionId, Order::getState, Order::getPaidAt)
                .containsExactlyInAnyOrder(tuple(10L, PAID, paidAt), tuple(11L, PAID, paidAt));
        assertThat(cartItemRepository.count()).isZero();
        assertThat(events.stream(OrderPaymentSucceedEvent.class))
                .extracting(
                        OrderPaymentSucceedEvent::auctionId,
                        OrderPaymentSucceedEvent::productId,
                        OrderPaymentSucceedEvent::winnerId,
                        OrderPaymentSucceedEvent::sellerId,
                        OrderPaymentSucceedEvent::paidAt)
                .containsExactlyInAnyOrder(tuple(10L, 20L, 1L, 2L, paidAt), tuple(11L, 21L, 1L, 4L, paidAt));
    }

    @Test
    @DisplayName("같은 그룹의 결제 완료 이벤트를 다시 받으면 ORDER_STATE_BAD_REQUEST로 끝나고, 처음 결제 시각이 그대로이며 결제 성공 이벤트는 주문마다 한 번만 발행된다")
    void paymentPaid_twice_firstPaymentKept() {
        Long groupId = saveWaitingGroupWithCartItems();
        orderKafkaListener.handle(paidEvent(groupId, paidAt));

        assertThatThrownBy(() -> orderKafkaListener.handle(paidEvent(groupId, paidAt.plusMinutes(5))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ORDER_STATE_BAD_REQUEST);

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

    private DeliveryStatusUpdatedEvent statusEvent(Long orderId, DeliveryStatusCode before, DeliveryStatusCode after, LocalDateTime updatedAt) {
        return new DeliveryStatusUpdatedEvent(orderId, 1L, 2L, before, after, updatedAt);
    }

    private Long saveOrder(OrderState state) {
        OrderMember winner = orderMemberRepository.save(new OrderMember(1L, winnerUuid, "낙찰자"));
        OrderMember seller = orderMemberRepository.save(new OrderMember(2L, sellerUuid, "판매자"));
        OrderGroup group = orderGroupRepository.save(new OrderGroup(winner, 33_000L));
        Order order = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return orderRepository.save(order).getId();
    }

    private Long saveWaitingGroupWithCartItems() {
        OrderMember winner = orderMemberRepository.save(new OrderMember(1L, winnerUuid, "낙찰자"));
        OrderMember seller = orderMemberRepository.save(new OrderMember(2L, sellerUuid, "판매자"));
        OrderMember otherSeller = orderMemberRepository.save(new OrderMember(4L, otherSellerUuid, "다른 판매자"));
        cartItemRepository.save(new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, winAt));
        cartItemRepository.save(new CartItem(winner, otherSeller, 11L, 21L, 50_000L, 4_000L, winAt));
        OrderGroup group = orderGroupRepository.save(new OrderGroup(winner, 87_000L));
        orderRepository.save(new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L));
        orderRepository.save(new Order(group, winner, otherSeller, 11L, 21L, 50_000L, 4_000L));
        return group.getId();
    }

    private PaymentPaidEvent paidEvent(Long groupId, LocalDateTime paidAt) {
        return PaymentPaidEvent.of(groupId, 7L, 1L, 87_000L, paidAt);
    }

    private OrderState stateOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getState();
    }
}
