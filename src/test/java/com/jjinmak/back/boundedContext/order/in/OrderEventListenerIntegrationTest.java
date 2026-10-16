package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.boundedContext.order.out.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.OrderRepository;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.cart.dto.CartItemDto;
import com.jjinmak.back.shared.cart.event.CartCreateOrderEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.PAID;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPING;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.AT_PICKUP;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.OUT_FOR_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
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

    UUID winnerUuid = UUID.randomUUID();
    UUID sellerUuid = UUID.randomUUID();
    UUID otherSellerUuid = UUID.randomUUID();

    LocalDateTime winAt = LocalDateTime.now();

    @AfterEach
    void tearDown() {
        orderRepository.deleteAllInBatch();
        orderGroupRepository.deleteAllInBatch();
        orderMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("트랜잭션 안에서 주문 생성 이벤트를 발행하면, 커밋 뒤 주문 그룹 1개와 WAITING 주문들이 생긴다")
    void cartCreateOrder_insideTransaction_createsGroupAndOrders() {
        syncMembers();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(createOrderEvent()));

        List<OrderGroup> groups = orderGroupRepository.findAll();
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getTotalPrice()).isEqualTo(87_000L);
        Long groupId = groups.get(0).getId();
        assertThat(orderRepository.findAll())
                .extracting(
                        order -> order.getGroup().getId(),
                        order -> order.getWinner().getId(),
                        order -> order.getSeller().getId(),
                        Order::getAuctionId,
                        Order::getProductId,
                        Order::getWinningPrice,
                        Order::getDeliveryFee,
                        Order::getState)
                .containsExactlyInAnyOrder(
                        tuple(groupId, 1L, 2L, 10L, 20L, 30_000L, 3_000L, WAITING),
                        tuple(groupId, 1L, 4L, 11L, 21L, 50_000L, 4_000L, WAITING));
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행한 주문 생성 이벤트는 리스너가 받지 않는다")
    void cartCreateOrder_outsideTransaction_ignored() {
        syncMembers();

        eventPublisher.publish(createOrderEvent());

        assertThat(orderGroupRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("회원 동기화보다 주문 생성 이벤트가 먼저 오면 주문 그룹도 주문도 생기지 않는다 (현재 동작)")
    void cartCreateOrder_beforeMemberSync_nothingStored() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(createOrderEvent()));

        assertThat(orderGroupRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("두 번째 판매자가 아직 동기화되지 않았으면, 먼저 저장된 주문 그룹과 첫 주문까지 모두 롤백된다")
    void cartCreateOrder_secondSellerNotSynced_rollsBackGroupAndOrders() {
        orderMemberRepository.save(new OrderMember(1L, winnerUuid, "낙찰자"));
        orderMemberRepository.save(new OrderMember(2L, sellerUuid, "판매자"));

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(createOrderEvent()));

        assertThat(orderGroupRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
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

    private void syncMembers() {
        orderMemberRepository.save(new OrderMember(1L, winnerUuid, "낙찰자"));
        orderMemberRepository.save(new OrderMember(2L, sellerUuid, "판매자"));
        orderMemberRepository.save(new OrderMember(4L, otherSellerUuid, "다른 판매자"));
    }

    private CartCreateOrderEvent createOrderEvent() {
        return new CartCreateOrderEvent(87_000L, List.of(
                cartItem(5L, sellerUuid, 10L, 20L, 30_000L, 3_000L),
                cartItem(6L, otherSellerUuid, 11L, 21L, 50_000L, 4_000L)));
    }

    private CartItemDto cartItem(Long id, UUID sellerId, Long auctionId, Long productId, Long winningPrice, Long deliveryFee) {
        return new CartItemDto(id, winnerUuid, sellerId, auctionId, productId, winningPrice, deliveryFee, winAt, winAt.plusDays(1));
    }

    private Long saveOrder(OrderState state) {
        OrderMember winner = orderMemberRepository.save(new OrderMember(1L, winnerUuid, "낙찰자"));
        OrderMember seller = orderMemberRepository.save(new OrderMember(2L, sellerUuid, "판매자"));
        OrderGroup group = orderGroupRepository.save(new OrderGroup(33_000L));
        Order order = new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
        order.updateState(state);
        return orderRepository.save(order).getId();
    }
}
