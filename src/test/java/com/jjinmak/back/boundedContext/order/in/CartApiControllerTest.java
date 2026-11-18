package com.jjinmak.back.boundedContext.order.in;

import com.jjinmak.back.boundedContext.order.domain.CartItem;
import com.jjinmak.back.boundedContext.order.domain.Order;
import com.jjinmak.back.boundedContext.order.domain.OrderGroup;
import com.jjinmak.back.boundedContext.order.domain.OrderMember;
import com.jjinmak.back.boundedContext.order.out.repository.CartItemRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.boundedContext.order.out.repository.OrderRepository;
import com.jjinmak.back.global.exception.BusinessException;
import com.jjinmak.back.global.security.JwtTokenProvider;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.back.shared.payment.dto.PaymentRequestDto;
import com.jjinmak.back.shared.payment.dto.PaymentResultDto;
import com.jjinmak.back.shared.payment.port.PaymentPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.boundedContext.order.domain.OrderState.WAITING;
import static com.jjinmak.back.global.exception.CommonErrorCode.CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class CartApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Autowired
    OrderMemberRepository orderMemberRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    OrderGroupRepository orderGroupRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    ApplicationEvents events;

    @MockitoBean
    PaymentPort paymentPort;

    OrderMember winner;
    OrderMember seller;

    Long myItemId;

    @BeforeEach
    void setUp() {
        winner = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));
        seller = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "판매자"));
        OrderMember other = orderMemberRepository.save(new OrderMember(3L, UUID.randomUUID(), "다른 낙찰자"));

        myItemId = cartItemRepository.save(new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())).getId();
        cartItemRepository.save(new CartItem(other, seller, 11L, 21L, 50_000L, 4_000L, LocalDateTime.now()));
    }

    @Test
    @DisplayName("토큰 없이 장바구니를 조회하면 401")
    void read_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON002"));
    }

    @Test
    @DisplayName("로그인한 회원은 자기 장바구니 항목만 조회한다")
    void read_onlyMyItems() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(myItemId))
                .andExpect(jsonPath("$.data[0].auctionId").value(10));
    }

    @Test
    @DisplayName("토큰은 유효하지만 주문 회원이 없으면 404 USER_NOT_FOUND")
    void read_memberNotSynced() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(99L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMON103"));
    }

    @Test
    @DisplayName("결제 요청: 상품 목록이 비어 있으면 400, 결제를 요청하지 않는다")
    void payment_emptyProductIds() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON001"));

        verifyNoInteractions(paymentPort);
    }

    @Test
    @DisplayName("결제 요청: 남의 상품이 섞여 있으면 403 CART_ITEM_FORBIDDEN(ORDER007), 주문 그룹과 주문을 만들지 않고 결제를 요청하지 않는다")
    void payment_withOthersItem() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20, 21]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER007"));

        assertThat(orderGroupRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
        verifyNoInteractions(paymentPort);
    }

    @Test
    @DisplayName("결제 요청: 결제 기한이 지난 상품이 섞여 있으면 403 CART_ITEM_PAYMENT_EXPIRED(ORDER008), 주문 그룹과 주문을 만들지 않고 결제를 요청하지 않는다")
    void payment_expiredItemMixed() throws Exception {
        cartItemRepository.save(new CartItem(winner, seller, 12L, 22L, 40_000L, 2_500L, LocalDateTime.now().minusDays(2)));

        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20, 22]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER008"));

        assertThat(orderGroupRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
        verifyNoInteractions(paymentPort);
    }

    @Test
    @DisplayName("결제 요청: 만든 주문 그룹 id, 낙찰자 id, 총액과 저장된 주문 id, 판매자 id, 금액을 결제 컨텍스트에 넘긴다")
    void payment_sendsPaymentRequest() throws Exception {
        when(paymentPort.requestPayment(any())).thenReturn(PaymentResultDto.paid(7L, 33_000L));

        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20]}"))
                .andExpect(status().isOk());

        OrderGroup group = onlyGroup();
        assertThat(group.getTotalPrice()).isEqualTo(33_000L);
        Long orderId = orderRepository.findAll().get(0).getId();

        ArgumentCaptor<PaymentRequestDto> captor = ArgumentCaptor.forClass(PaymentRequestDto.class);
        verify(paymentPort).requestPayment(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PaymentRequestDto(group.getId(), 1L, 33_000L,
                List.of(new PaymentRequestDto.Item(orderId, 2L, 30_000L, 3_000L))));
    }

    @Test
    @DisplayName("결제 요청: 예치금으로 결제되면(PAID) 200, 주문 그룹 id와 isPaid=true를 응답하고, 주문은 WAITING, 장바구니 항목은 그대로이며 이벤트를 발행하지 않는다 (결제 완료 처리는 결제 완료 이벤트가 맡는다)")
    void payment_paid() throws Exception {
        when(paymentPort.requestPayment(any())).thenReturn(PaymentResultDto.paid(7L, 33_000L));

        MvcResult result = mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaid").value(true))
                .andExpect(jsonPath("$.data.data").isEmpty())
                .andReturn();

        Long groupId = onlyGroup().getId();
        jsonPath("$.data.orderGroupId").value(groupId).match(result);
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
                .containsExactly(tuple(groupId, 1L, 2L, 10L, 20L, 30_000L, 3_000L, WAITING));
        assertThat(cartItemRepository.findAll()).extracting(CartItem::getAuctionId).containsExactlyInAnyOrder(10L, 11L);
        assertThat(events.stream(OrderPaymentSucceedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("결제 요청: 예치금이 부족하면(NEEDS_CHARGE) 200, 주문 그룹 id와 isPaid=false, 충전에 필요한 결제 정보를 응답하고, 주문은 WAITING, 장바구니 항목은 그대로이며 이벤트를 발행하지 않는다")
    void payment_needsCharge() throws Exception {
        when(paymentPort.requestPayment(any()))
                .thenReturn(PaymentResultDto.needsCharge(7L, 33_000L, 13_000L, "CHG-1", "찐막 주문 결제 (부족분 충전)"));

        MvcResult result = mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaid").value(false))
                .andExpect(jsonPath("$.data.data.paymentId").value(7))
                .andExpect(jsonPath("$.data.data.totalPrice").value(33_000))
                .andExpect(jsonPath("$.data.data.chargeAmount").value(13_000))
                .andExpect(jsonPath("$.data.data.pgOrderId").value("CHG-1"))
                .andExpect(jsonPath("$.data.data.orderName").value("찐막 주문 결제 (부족분 충전)"))
                .andReturn();

        Long groupId = onlyGroup().getId();
        jsonPath("$.data.orderGroupId").value(groupId).match(result);
        assertThat(orderRepository.findAll()).extracting(Order::getState).containsExactly(WAITING);
        assertThat(cartItemRepository.findAll()).extracting(CartItem::getAuctionId).containsExactlyInAnyOrder(10L, 11L);
        assertThat(events.stream(OrderPaymentSucceedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("결제 요청: 결제 컨텍스트가 예외로 끝나면 그 에러로 응답하고, 이미 만든 주문은 WAITING, 장바구니 항목은 그대로이며 이벤트를 발행하지 않는다")
    void payment_paymentFails() throws Exception {
        when(paymentPort.requestPayment(any())).thenThrow(new BusinessException(CONFLICT));

        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMMON005"));

        onlyGroup();
        assertThat(orderRepository.findAll()).extracting(Order::getState).containsExactly(WAITING);
        assertThat(cartItemRepository.findAll()).extracting(CartItem::getAuctionId).containsExactlyInAnyOrder(10L, 11L);
        assertThat(events.stream(OrderPaymentSucceedEvent.class)).isEmpty();
    }

    private OrderGroup onlyGroup() {
        List<OrderGroup> groups = orderGroupRepository.findAll();
        assertThat(groups).hasSize(1);
        return groups.get(0);
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(memberId, List.of("ROLE_BUYER"));
    }
}
