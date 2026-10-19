package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.domain.OrderState;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.boundedContext.payment.in.PaymentEventListener;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.security.JwtTokenProvider;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.DELIVERY_ERROR;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.PAID;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.REFUND_REQUESTED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.SHIPPED;
import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static com.jjinmak.back.shared.delivery.domain.CarrierType.CJ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class OrderApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    OrderMemberRepository orderMemberRepository;

    @Autowired
    OrderGroupRepository orderGroupRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    ApplicationEvents events;

    @Autowired
    EntityManager em;

    @MockitoBean
    SettlementEventListener settlementEventListener;

    @MockitoBean
    PaymentEventListener paymentEventListener;

    OrderMember winner;
    OrderMember seller;

    Long paidOrderId;
    Long shippedOrderId;
    Long waitingOrderId;

    @BeforeEach
    void setUp() {
        winner = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));
        seller = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "판매자"));
        OrderMember other = orderMemberRepository.save(new OrderMember(3L, UUID.randomUUID(), "다른 회원"));
        OrderGroup group = orderGroupRepository.save(new OrderGroup(152_000L));
        OrderGroup otherGroup = orderGroupRepository.save(new OrderGroup(76_000L));

        paidOrderId = saveOrder(group, winner, seller, 10L, 20L, 30_000L, 3_000L, PAID);
        shippedOrderId = saveOrder(group, winner, seller, 11L, 21L, 50_000L, 4_000L, SHIPPED);
        waitingOrderId = saveOrder(group, winner, seller, 12L, 22L, 60_000L, 5_000L, WAITING);
        saveOrder(otherGroup, other, winner, 13L, 23L, 70_000L, 6_000L, PAID);

        setCreatedAt(paidOrderId, LocalDateTime.of(2026, 10, 15, 11, 0));
        setCreatedAt(shippedOrderId, LocalDateTime.of(2026, 10, 15, 10, 0));
    }

    @Test
    @DisplayName("토큰 없이 주문 목록을 조회하면 401")
    void readWinnerOrders_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/order/winner"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    @Test
    @DisplayName("낙찰자 주문 목록: 내가 낙찰한 결제 이후 주문만 최신순으로 나오고, 결제 대기 주문과 내가 판매한 주문은 빠진다")
    void readWinnerOrders_onlyMyPaidOrders() throws Exception {
        mockMvc.perform(get("/api/v1/order/winner")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(paidOrderId))
                .andExpect(jsonPath("$.data[0].winnerId").value(winner.getUuid().toString()))
                .andExpect(jsonPath("$.data[0].sellerId").value(seller.getUuid().toString()))
                .andExpect(jsonPath("$.data[0].state").value("PAID"))
                .andExpect(jsonPath("$.data[1].id").value(shippedOrderId))
                .andExpect(jsonPath("$.data[1].state").value("SHIPPED"));
    }

    @Test
    @DisplayName("판매자 주문 목록: 내가 판매한 결제 이후 주문만 최신순으로 나오고, 결제 대기 주문과 다른 판매자의 주문은 빠진다")
    void readSellerOrders_onlyMySoldOrders() throws Exception {
        mockMvc.perform(get("/api/v1/order/seller")
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(paidOrderId))
                .andExpect(jsonPath("$.data[0].winnerId").value(winner.getUuid().toString()))
                .andExpect(jsonPath("$.data[0].sellerId").value(seller.getUuid().toString()))
                .andExpect(jsonPath("$.data[1].id").value(shippedOrderId));
    }

    @Test
    @DisplayName("주문 상세: 낙찰자는 자기 주문을 조회한다")
    void readOrder_byWinner() throws Exception {
        mockMvc.perform(get("/api/v1/order/{orderId}", paidOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(paidOrderId))
                .andExpect(jsonPath("$.data.winnerId").value(winner.getUuid().toString()))
                .andExpect(jsonPath("$.data.sellerId").value(seller.getUuid().toString()))
                .andExpect(jsonPath("$.data.productId").value(20))
                .andExpect(jsonPath("$.data.winningPrice").value(30_000))
                .andExpect(jsonPath("$.data.deliveryFee").value(3_000))
                .andExpect(jsonPath("$.data.state").value("PAID"));
    }

    @Test
    @DisplayName("주문 상세: 판매자도 자기가 판매한 주문을 조회한다")
    void readOrder_bySeller() throws Exception {
        mockMvc.perform(get("/api/v1/order/{orderId}", paidOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(paidOrderId));
    }

    @Test
    @DisplayName("주문 상세: 낙찰자도 판매자도 아닌 회원이 조회하면 403 ORDER_FORBIDDEN")
    void readOrder_byOther() throws Exception {
        mockMvc.perform(get("/api/v1/order/{orderId}", paidOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(3L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER002"));
    }

    @Test
    @DisplayName("주문 상세: 결제 대기 중인 주문은 낙찰자가 조회해도 404 ORDER_NOT_FOUND")
    void readOrder_waiting() throws Exception {
        mockMvc.perform(get("/api/v1/order/{orderId}", waitingOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER001"));
    }

    @Test
    @DisplayName("주문 상세: 토큰은 유효하지만 주문 회원이 없으면 404 USER_NOT_FOUND")
    void readOrder_memberNotSynced() throws Exception {
        mockMvc.perform(get("/api/v1/order/{orderId}", paidOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(99L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMON103"));
    }

    @Test
    @DisplayName("구매 확정: 배송 완료된 주문을 낙찰자가 확정하면 200, CONFIRMED가 되고, 구매 확정 이벤트를 한 번 발행한다")
    void confirmPurchase_success() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/confirm-purchase", shippedOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk());

        assertThat(stateOf(shippedOrderId)).isEqualTo(CONFIRMED);
        List<OrderConfirmPurchaseEvent> published = events.stream(OrderConfirmPurchaseEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).orderId()).isEqualTo(shippedOrderId);
        assertThat(published.get(0).winnerId()).isEqualTo(1L);
        assertThat(published.get(0).sellerId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("구매 확정: 판매자가 확정하면 403 ORDER_FORBIDDEN, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void confirmPurchase_bySeller() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/confirm-purchase", shippedOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER002"));

        assertThat(stateOf(shippedOrderId)).isEqualTo(SHIPPED);
        assertThat(events.stream(OrderConfirmPurchaseEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("구매 확정: 배송 완료 전인 주문을 확정하면 400 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void confirmPurchase_notShipped() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/confirm-purchase", paidOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORDER003"));

        assertThat(stateOf(paidOrderId)).isEqualTo(PAID);
        assertThat(events.stream(OrderConfirmPurchaseEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("환불 요청: 배송 완료된 주문에 낙찰자가 요청하면 200, REFUND_REQUESTED가 되고, 환불 요청 이벤트를 한 번 발행한다")
    void requestRefund_success() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/request-refund", shippedOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk());

        assertThat(stateOf(shippedOrderId)).isEqualTo(REFUND_REQUESTED);
        List<OrderRefundRequestedEvent> published = events.stream(OrderRefundRequestedEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).orderId()).isEqualTo(shippedOrderId);
        assertThat(published.get(0).winnerId()).isEqualTo(1L);
        assertThat(published.get(0).sellerId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("환불 요청: 판매자가 요청하면 403 ORDER_FORBIDDEN, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void requestRefund_bySeller() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/request-refund", shippedOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER002"));

        assertThat(stateOf(shippedOrderId)).isEqualTo(SHIPPED);
        assertThat(events.stream(OrderRefundRequestedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("환불 요청: 배송 완료 전인 주문에 요청하면 400 ORDER_STATE_BAD_REQUEST, 상태가 바뀌지 않고 이벤트를 발행하지 않는다")
    void requestRefund_notShipped() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/request-refund", paidOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORDER003"));

        assertThat(stateOf(paidOrderId)).isEqualTo(PAID);
        assertThat(events.stream(OrderRefundRequestedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("상품 발송: 결제 완료된 주문에 판매자가 송장을 등록하면 200, 발송 이벤트를 한 번 발행하고 주문은 PAID 그대로다")
    void requestDelivery_success() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/send-product", paidOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"CJ\", \"waybill\": \"1234567890\"}"))
                .andExpect(status().isOk());

        assertThat(stateOf(paidOrderId)).isEqualTo(PAID);
        List<OrderSendProductEvent> published = events.stream(OrderSendProductEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).orderId()).isEqualTo(paidOrderId);
        assertThat(published.get(0).winnerId()).isEqualTo(1L);
        assertThat(published.get(0).sellerId()).isEqualTo(2L);
        assertThat(published.get(0).carrier()).isEqualTo(CJ);
        assertThat(published.get(0).waybill()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("상품 발송: 낙찰자가 송장을 등록하면 403 ORDER_FORBIDDEN, 발송 이벤트를 발행하지 않는다")
    void requestDelivery_byWinner() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/send-product", paidOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"CJ\", \"waybill\": \"1234567890\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER002"));

        assertThat(events.stream(OrderSendProductEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("상품 발송: 송장번호가 비어 있으면 400, 발송 이벤트를 발행하지 않는다")
    void requestDelivery_blankWaybill() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/send-product", paidOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"CJ\", \"waybill\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON001"));

        assertThat(events.stream(OrderSendProductEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("상품 발송: 없는 택배사 값이면 400, 발송 이벤트를 발행하지 않는다")
    void requestDelivery_unknownCarrier() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/send-product", paidOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"UNKNOWN\", \"waybill\": \"1234567890\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON001"));

        assertThat(events.stream(OrderSendProductEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("상품 발송: 결제 완료 상태가 아닌 주문이면 400 ORDER_STATE_BAD_REQUEST, 발송 이벤트를 발행하지 않는다")
    void requestDelivery_notPaid() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/send-product", shippedOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"CJ\", \"waybill\": \"1234567890\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORDER003"));

        assertThat(events.stream(OrderSendProductEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("상품 발송: 배송 오류(DELIVERY_ERROR) 주문이면 400 ORDER_STATE_BAD_REQUEST, 주문은 그대로이고 발송 이벤트를 발행하지 않는다")
    void requestDelivery_deliveryError() throws Exception {
        Long deliveryErrorOrderId = saveDeliveryErrorOrder();

        mockMvc.perform(post("/api/v1/order/{orderId}/send-product", deliveryErrorOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"CJ\", \"waybill\": \"1234567890\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORDER003"));

        assertThat(stateOf(deliveryErrorOrderId)).isEqualTo(DELIVERY_ERROR);
        assertThat(events.stream(OrderSendProductEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("상품 발송: 결제 대기 주문이면 400 ORDER_STATE_BAD_REQUEST, 주문은 그대로이고 발송 이벤트를 발행하지 않는다")
    void requestDelivery_waiting() throws Exception {
        mockMvc.perform(post("/api/v1/order/{orderId}/send-product", waitingOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"CJ\", \"waybill\": \"1234567890\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORDER003"));

        assertThat(stateOf(waitingOrderId)).isEqualTo(WAITING);
        assertThat(events.stream(OrderSendProductEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("주문 목록: 배송 오류(DELIVERY_ERROR) 주문도 낙찰자와 판매자의 목록에 최신순으로 나온다")
    void readOrders_includeDeliveryError() throws Exception {
        Long deliveryErrorOrderId = saveDeliveryErrorOrder();
        setCreatedAt(deliveryErrorOrderId, LocalDateTime.of(2026, 10, 15, 12, 0));

        mockMvc.perform(get("/api/v1/order/winner")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(deliveryErrorOrderId))
                .andExpect(jsonPath("$.data[0].state").value("DELIVERY_ERROR"));

        mockMvc.perform(get("/api/v1/order/seller")
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(deliveryErrorOrderId))
                .andExpect(jsonPath("$.data[0].state").value("DELIVERY_ERROR"));
    }

    @Test
    @DisplayName("주문 상세: 배송 오류(DELIVERY_ERROR) 주문도 판매자와 낙찰자가 조회할 수 있다")
    void readOrder_deliveryError() throws Exception {
        Long deliveryErrorOrderId = saveDeliveryErrorOrder();

        mockMvc.perform(get("/api/v1/order/{orderId}", deliveryErrorOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(deliveryErrorOrderId))
                .andExpect(jsonPath("$.data.state").value("DELIVERY_ERROR"));

        mockMvc.perform(get("/api/v1/order/{orderId}", deliveryErrorOrderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(deliveryErrorOrderId));
    }

    private Long saveDeliveryErrorOrder() {
        OrderGroup group = orderGroupRepository.save(new OrderGroup(80_000L));
        return saveOrder(group, winner, seller, 14L, 24L, 75_000L, 5_000L, DELIVERY_ERROR);
    }

    private Long saveOrder(OrderGroup group, OrderMember winner, OrderMember seller, Long auctionId, Long productId, Long winningPrice, Long deliveryFee, OrderState state) {
        Order order = new Order(group, winner, seller, auctionId, productId, winningPrice, deliveryFee);
        order.updateState(state);
        return orderRepository.save(order).getId();
    }

    private void setCreatedAt(Long orderId, LocalDateTime createdAt) {
        em.createQuery("update Order o set o.createdAt = :createdAt where o.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", orderId)
                .executeUpdate();
    }

    private OrderState stateOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getState();
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, List.of("ROLE_BUYER"));
    }
}
