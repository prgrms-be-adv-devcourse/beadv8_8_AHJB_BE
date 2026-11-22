package com.jjinmak.back.order.in;

import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.common.security.HeaderAuthenticationFilter;
import com.jjinmak.contracts.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.contracts.payment.dto.PaymentCloseResultDto;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.back.order.domain.CartItem;
import com.jjinmak.back.order.domain.Order;
import com.jjinmak.back.order.domain.OrderGroup;
import com.jjinmak.back.order.domain.OrderMember;
import com.jjinmak.back.order.out.repository.CartItemRepository;
import com.jjinmak.back.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.order.out.repository.OrderRepository;
import com.jjinmak.back.shared.payment.port.PaymentPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.back.order.domain.OrderState.WAITING;
import static com.jjinmak.back.payment.exception.WalletErrorCode.WALLET_CONCURRENT_MODIFICATION;
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
@Transactional
@RecordApplicationEvents
class CartApiControllerTest {

    @Autowired
    WebApplicationContext context;

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

    MockMvc mockMvc;

    OrderMember winner;
    OrderMember seller;

    Long myItemId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new HeaderAuthenticationFilter())
                .build();

        winner = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));
        seller = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "판매자"));
        OrderMember other = orderMemberRepository.save(new OrderMember(3L, UUID.randomUUID(), "다른 낙찰자"));

        myItemId = cartItemRepository.save(new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now())).getId();
        cartItemRepository.save(new CartItem(other, seller, 11L, 21L, 50_000L, 4_000L, LocalDateTime.now()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("로그인한 회원은 자기 장바구니 항목만 조회한다")
    void read_onlyMyItems() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header(AuthHeaders.MEMBER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(myItemId))
                .andExpect(jsonPath("$.data[0].auctionId").value(10));
    }

    @Test
    @DisplayName("결제 기한이 지난 내 항목은 지워지기 전이어도 장바구니 조회에 나오지 않는다")
    void read_hidesExpiredItems() throws Exception {
        cartItemRepository.save(new CartItem(winner, seller, 12L, 22L, 40_000L, 2_500L, LocalDateTime.now().minusDays(1).minusMinutes(30)));

        mockMvc.perform(get("/api/v1/cart")
                        .header(AuthHeaders.MEMBER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(myItemId));
    }

    @Test
    @DisplayName("회원 헤더는 있지만 주문 회원이 없으면 404 USER_NOT_FOUND")
    void read_memberNotSynced() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header(AuthHeaders.MEMBER_ID, 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMON103"));
    }

    @Test
    @DisplayName("결제 요청: 상품 목록이 비어 있으면 400, 결제를 요청하지 않는다")
    void payment_emptyProductIds() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [], \"pointUseAmount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON001"));

        verifyNoInteractions(paymentPort);
    }

    @Test
    @DisplayName("결제 요청: 포인트 사용액을 보내지 않으면 400, 주문 그룹과 주문을 만들지 않고 결제를 요청하지 않는다")
    void payment_withoutPointUseAmount() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20], \"paymentMethod\": \"CARD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON001"));

        assertThat(orderGroupRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
        verifyNoInteractions(paymentPort);
    }

    @Test
    @DisplayName("결제 요청: 포인트 사용액이 음수면 400, 주문 그룹과 주문을 만들지 않고 결제를 요청하지 않는다")
    void payment_negativePointUseAmount() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20], \"pointUseAmount\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON001"));

        assertThat(orderGroupRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
        verifyNoInteractions(paymentPort);
    }

    @Test
    @DisplayName("결제 요청: 남의 상품이 섞여 있으면 403 CART_ITEM_FORBIDDEN(ORDER007), 주문 그룹과 주문을 만들지 않고 결제를 요청하지 않는다")
    void payment_withOthersItem() throws Exception {
        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20, 21], \"pointUseAmount\": 0}"))
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
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20, 22], \"pointUseAmount\": 0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER008"));

        assertThat(orderGroupRepository.count()).isZero();
        assertThat(orderRepository.count()).isZero();
        verifyNoInteractions(paymentPort);
    }

    @Test
    @DisplayName("결제 요청: 만든 주문 그룹 id, 낙찰자 id, 총액과 저장된 주문 id, 판매자 id, 금액에 요청받은 결제수단과 포인트 사용액을 담아 결제 서비스에 넘긴다")
    void payment_sendsPaymentRequest() throws Exception {
        when(paymentPort.requestPayment(any())).thenReturn(PaymentResultDto.paid(7L, 33_000L));

        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20], \"paymentMethod\": \"CARD\", \"pointUseAmount\": 5000}"))
                .andExpect(status().isOk());

        OrderGroup group = onlyGroup();
        assertThat(group.getTotalPrice()).isEqualTo(33_000L);
        Long orderId = orderRepository.findAll().get(0).getId();

        ArgumentCaptor<PaymentRequestDto> captor = ArgumentCaptor.forClass(PaymentRequestDto.class);
        verify(paymentPort).requestPayment(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PaymentRequestDto(group.getId(), 1L, 33_000L,
                List.of(new PaymentRequestDto.Item(orderId, 2L, 30_000L, 3_000L)), "CARD", 5_000L));
    }

    @Test
    @DisplayName("결제 요청: 결제수단을 보내지 않으면 200, 결제수단을 비운(null) 채 포인트 사용액과 함께 결제 서비스에 넘긴다")
    void payment_withoutPaymentMethod() throws Exception {
        when(paymentPort.requestPayment(any())).thenReturn(PaymentResultDto.paid(7L, 33_000L));

        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20], \"pointUseAmount\": 0}"))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentRequestDto> captor = ArgumentCaptor.forClass(PaymentRequestDto.class);
        verify(paymentPort).requestPayment(captor.capture());
        assertThat(captor.getValue().payMethod()).isNull();
        assertThat(captor.getValue().pointUseAmount()).isZero();
    }

    @Test
    @DisplayName("결제 요청: 예치금으로 결제되면(PAID) 200, 주문 그룹 id와 isPaid=true를 응답하고, 주문은 WAITING, 장바구니 항목은 그대로이며 이벤트를 발행하지 않는다 (결제 완료 처리는 결제 완료 이벤트가 맡는다)")
    void payment_paid() throws Exception {
        when(paymentPort.requestPayment(any())).thenReturn(PaymentResultDto.paid(7L, 33_000L));

        MvcResult result = mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20], \"pointUseAmount\": 0}"))
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
    @DisplayName("결제 요청: 예치금이 부족하면(NEEDS_CHARGE) 200, 주문 그룹 id와 isPaid=false, 충전에 필요한 결제 정보, 결제창을 열 결제수단, 적용된 결제수단, 포인트·예치금·직접 결제 금액을 응답하고, 주문은 WAITING, 장바구니 항목은 그대로이며 이벤트를 발행하지 않는다")
    void payment_needsCharge() throws Exception {
        when(paymentPort.requestPayment(any()))
                .thenReturn(PaymentResultDto.needsCharge(7L, 33_000L, 13_000L, 13_000L, "CHG-1", "찐막 주문 결제 (부족분 충전)", "TRANSFER",
                        new PaymentResultDto.Breakdown("DEPOSIT", 3_000L, 30_000L, 0L)));

        MvcResult result = mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20], \"paymentMethod\": \"DEPOSIT\", \"pointUseAmount\": 3000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaid").value(false))
                .andExpect(jsonPath("$.data.data.paymentId").value(7))
                .andExpect(jsonPath("$.data.data.totalPrice").value(33_000))
                .andExpect(jsonPath("$.data.data.chargeAmount").value(13_000))
                .andExpect(jsonPath("$.data.data.pgOrderId").value("CHG-1"))
                .andExpect(jsonPath("$.data.data.orderName").value("찐막 주문 결제 (부족분 충전)"))
                .andExpect(jsonPath("$.data.data.pgMethod").value("TRANSFER"))
                .andExpect(jsonPath("$.data.data.payMethod").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.data.pointAmount").value(3_000))
                .andExpect(jsonPath("$.data.data.depositAmount").value(30_000))
                .andExpect(jsonPath("$.data.data.pgAmount").value(0))
                .andReturn();

        Long groupId = onlyGroup().getId();
        jsonPath("$.data.orderGroupId").value(groupId).match(result);
        assertThat(orderRepository.findAll()).extracting(Order::getState).containsExactly(WAITING);
        assertThat(cartItemRepository.findAll()).extracting(CartItem::getAuctionId).containsExactlyInAnyOrder(10L, 11L);
        assertThat(events.stream(OrderPaymentSucceedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("결제 요청: 직접 결제면(NEEDS_PAYMENT) 200, 주문 그룹 id와 isPaid=false, 결제창 금액, 결제창을 열 결제수단, 적용된 결제수단, 포인트·예치금·직접 결제 금액을 응답하고, 주문은 WAITING, 장바구니 항목은 그대로이며 이벤트를 발행하지 않는다")
    void payment_needsPayment() throws Exception {
        when(paymentPort.requestPayment(any()))
                .thenReturn(PaymentResultDto.needsPayment(7L, 33_000L, "CHG-2", "찐막 주문 결제", "CARD",
                        new PaymentResultDto.Breakdown("CARD", 3_000L, 0L, 30_000L)));

        MvcResult result = mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20], \"paymentMethod\": \"CARD\", \"pointUseAmount\": 3000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaid").value(false))
                .andExpect(jsonPath("$.data.data.paymentId").value(7))
                .andExpect(jsonPath("$.data.data.totalPrice").value(33_000))
                .andExpect(jsonPath("$.data.data.chargeAmount").value(30_000))
                .andExpect(jsonPath("$.data.data.pgOrderId").value("CHG-2"))
                .andExpect(jsonPath("$.data.data.orderName").value("찐막 주문 결제"))
                .andExpect(jsonPath("$.data.data.pgMethod").value("CARD"))
                .andExpect(jsonPath("$.data.data.payMethod").value("CARD"))
                .andExpect(jsonPath("$.data.data.pointAmount").value(3_000))
                .andExpect(jsonPath("$.data.data.depositAmount").value(0))
                .andExpect(jsonPath("$.data.data.pgAmount").value(30_000))
                .andReturn();

        Long groupId = onlyGroup().getId();
        jsonPath("$.data.orderGroupId").value(groupId).match(result);
        assertThat(orderRepository.findAll()).extracting(Order::getState).containsExactly(WAITING);
        assertThat(cartItemRepository.findAll()).extracting(CartItem::getAuctionId).containsExactlyInAnyOrder(10L, 11L);
        assertThat(events.stream(OrderPaymentSucceedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("결제 요청: 결제가 결제 쪽 예외로 실패하면 그 에러 코드(409 WALLET501)를 그대로 응답하고, 이미 만든 주문은 WAITING, 장바구니 항목은 그대로이며 이벤트를 발행하지 않는다")
    void payment_paymentFails() throws Exception {
        when(paymentPort.requestPayment(any())).thenThrow(new BusinessException(WALLET_CONCURRENT_MODIFICATION));

        mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": [20], \"pointUseAmount\": 0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WALLET501"));

        onlyGroup();
        assertThat(orderRepository.findAll()).extracting(Order::getState).containsExactly(WAITING);
        assertThat(cartItemRepository.findAll()).extracting(CartItem::getAuctionId).containsExactlyInAnyOrder(10L, 11L);
        assertThat(events.stream(OrderPaymentSucceedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("결제 취소: 결제를 닫았으면(CLOSED) 204, 주문 그룹 id와 로그인한 회원 id로 결제를 닫고 주문 그룹과 주문을 삭제하며 장바구니 항목은 그대로 둔다")
    void cancel_closed() throws Exception {
        Long groupId = waitingGroup().getId();
        when(paymentPort.closePayment(groupId, 1L)).thenReturn(PaymentCloseResultDto.closed(groupId, 7L));

        mockMvc.perform(cancel(1L, "{\"orderGroupId\": " + groupId + "}"))
                .andExpect(status().isNoContent());

        verify(paymentPort).closePayment(groupId, 1L);
        assertThat(orderGroupRepository.findById(groupId)).isEmpty();
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(cartItemRepository.findAll()).extracting(CartItem::getAuctionId).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("결제 취소: 이미 결제가 끝났으면(ALREADY_PAID) 400 ORDER_STATE_BAD_REQUEST(ORDER003), 주문 그룹과 주문을 삭제하지 않는다")
    void cancel_alreadyPaid() throws Exception {
        Long groupId = waitingGroup().getId();
        when(paymentPort.closePayment(groupId, 1L)).thenReturn(PaymentCloseResultDto.alreadyPaid(groupId, 7L));

        mockMvc.perform(cancel(1L, "{\"orderGroupId\": " + groupId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORDER003"));

        assertThat(orderGroupRepository.findById(groupId)).isPresent();
        assertThat(orderRepository.findAll()).extracting(Order::getState).containsExactly(WAITING);
    }

    @Test
    @DisplayName("결제 취소: 다른 회원의 주문 그룹이면 403 ORDER_GROUP_FORBIDDEN(ORDER009), 결제를 닫지 않고 주문 그룹을 삭제하지 않는다")
    void cancel_othersGroup() throws Exception {
        Long groupId = waitingGroup().getId();

        mockMvc.perform(cancel(3L, "{\"orderGroupId\": " + groupId + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER009"));

        verifyNoInteractions(paymentPort);
        assertThat(orderGroupRepository.findById(groupId)).isPresent();
    }

    @Test
    @DisplayName("결제 취소: 주문 그룹 id를 보내지 않으면 400, 결제를 닫지 않고 주문 그룹을 삭제하지 않는다")
    void cancel_withoutOrderGroupId() throws Exception {
        Long groupId = waitingGroup().getId();

        mockMvc.perform(cancel(1L, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON001"));

        verifyNoInteractions(paymentPort);
        assertThat(orderGroupRepository.findById(groupId)).isPresent();
    }

    @Test
    @DisplayName("결제 취소: 없는 주문 그룹이면 404 ORDER_GROUP_NOT_FOUND(ORDER004), 결제를 닫지 않는다")
    void cancel_groupNotFound() throws Exception {
        mockMvc.perform(cancel(1L, "{\"orderGroupId\": 999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER004"));

        verifyNoInteractions(paymentPort);
    }

    private MockHttpServletRequestBuilder cancel(Long memberId, String body) {
        return post("/api/v1/cart")
                .header(AuthHeaders.MEMBER_ID, memberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private OrderGroup waitingGroup() {
        OrderGroup group = orderGroupRepository.save(new OrderGroup(winner, 33_000L));
        group.add(orderRepository.save(new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L)));
        return group;
    }

    private OrderGroup onlyGroup() {
        List<OrderGroup> groups = orderGroupRepository.findAll();
        assertThat(groups).hasSize(1);
        return groups.get(0);
    }
}
