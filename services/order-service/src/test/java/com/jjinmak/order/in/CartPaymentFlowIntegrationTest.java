package com.jjinmak.order.in;

import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.common.security.HeaderAuthenticationFilter;
import com.jjinmak.contracts.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.contracts.payment.dto.PaymentRequestDto;
import com.jjinmak.contracts.payment.dto.PaymentResultDto;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.order.domain.CartItem;
import com.jjinmak.order.domain.Order;
import com.jjinmak.order.domain.OrderMember;
import com.jjinmak.order.out.payment.PaymentRequest;
import com.jjinmak.order.out.repository.CartItemRepository;
import com.jjinmak.order.out.repository.OrderGroupRepository;
import com.jjinmak.order.out.repository.OrderMemberRepository;
import com.jjinmak.order.out.repository.OrderRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.jjinmak.order.domain.OrderState.PAID;
import static com.jjinmak.order.domain.OrderState.WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@RecordApplicationEvents
class CartPaymentFlowIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    OrderKafkaListener orderKafkaListener;

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
    PaymentRequest paymentRequest;

    MockMvc mockMvc;

    LocalDateTime paidAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new HeaderAuthenticationFilter())
                .build();

        OrderMember winner = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));
        OrderMember seller = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "판매자"));
        cartItemRepository.save(new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now()));
        cartItemRepository.save(new CartItem(winner, seller, 11L, 21L, 50_000L, 4_000L, LocalDateTime.now()));
        when(paymentRequest.requestPayment(any())).thenReturn(PaymentResultDto.paid(7L, 87_000L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        orderRepository.deleteAllInBatch();
        orderGroupRepository.deleteAllInBatch();
        cartItemRepository.deleteAllInBatch();
        orderMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("예치금 전액 결제: 결제 서비스가 PAID를 돌려주면 200 isPaid=true이고 결제 완료 이벤트를 받기 전까지 주문은 WAITING, 장바구니 항목은 그대로다. 결제 완료 이벤트를 받으면 주문은 결제 시각과 함께 PAID, 장바구니 항목은 지워지며 결제 성공 이벤트가 주문마다 한 번 발행된다")
    void depositOnly_completesOrdersWhenPaidEventArrives() throws Exception {
        mockMvc.perform(payment())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaid").value(true));

        assertThat(orderRepository.findAll()).extracting(Order::getState).containsExactly(WAITING, WAITING);
        assertThat(cartItemRepository.count()).isEqualTo(2);
        assertThat(events.stream(OrderPaymentSucceedEvent.class)).isEmpty();

        orderKafkaListener.handle(paidEvent(sentRequest()));

        assertThat(orderRepository.findAll())
                .extracting(Order::getAuctionId, Order::getState, Order::getPaidAt)
                .containsExactlyInAnyOrder(tuple(10L, PAID, paidAt), tuple(11L, PAID, paidAt));
        assertThat(cartItemRepository.count()).isZero();
        assertThat(events.stream(OrderPaymentSucceedEvent.class))
                .extracting(OrderPaymentSucceedEvent::auctionId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @DisplayName("예치금 전액 결제 뒤 결제 완료 이벤트까지 받은 상품으로 다시 결제를 요청하면 403 CART_ITEM_FORBIDDEN(ORDER007), 결제를 다시 요청하지 않고 주문 그룹도 더 만들지 않는다")
    void depositOnly_retryAfterPaidEvent_rejectedWithoutPayingAgain() throws Exception {
        mockMvc.perform(payment())
                .andExpect(status().isOk());
        orderKafkaListener.handle(paidEvent(sentRequest()));

        mockMvc.perform(payment())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORDER007"));

        verify(paymentRequest, times(1)).requestPayment(any());
        assertThat(orderGroupRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("부족분 충전이 필요해 결제창으로 간 뒤 같은 상품을 순서만 바꿔 다시 결제 요청하면, 같은 주문 그룹과 같은 주문으로 결제를 요청하고 주문 그룹과 주문을 더 만들지 않으며, 그 그룹의 결제 완료 이벤트를 받으면 주문이 한 번씩 PAID가 된다")
    void needsCharge_retry_reusesGroupAndOrders() throws Exception {
        doReturn(PaymentResultDto.needsCharge(7L, 87_000L, 37_000L, "CHG-1", "찐막 주문 결제 (부족분 충전)"))
                .when(paymentRequest).requestPayment(any());

        mockMvc.perform(payment())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaid").value(false));
        mockMvc.perform(payment("[21, 20]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaid").value(false));

        ArgumentCaptor<PaymentRequestDto> captor = ArgumentCaptor.forClass(PaymentRequestDto.class);
        verify(paymentRequest, times(2)).requestPayment(captor.capture());
        PaymentRequestDto first = captor.getAllValues().get(0);
        assertThat(captor.getAllValues().get(1)).isEqualTo(first);
        assertThat(orderGroupRepository.count()).isEqualTo(1);
        assertThat(orderRepository.count()).isEqualTo(2);

        orderKafkaListener.handle(paidEvent(first));

        assertThat(orderRepository.findAll())
                .extracting(Order::getAuctionId, Order::getState, Order::getPaidAt)
                .containsExactlyInAnyOrder(tuple(10L, PAID, paidAt), tuple(11L, PAID, paidAt));
        assertThat(cartItemRepository.count()).isZero();
        assertThat(events.stream(OrderPaymentSucceedEvent.class))
                .extracting(OrderPaymentSucceedEvent::auctionId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    private MockHttpServletRequestBuilder payment() {
        return payment("[20, 21]");
    }

    private MockHttpServletRequestBuilder payment(String productIds) {
        return post("/api/v1/cart/payment")
                .header(AuthHeaders.MEMBER_ID, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productIds\": " + productIds + "}");
    }

    private PaymentRequestDto sentRequest() {
        ArgumentCaptor<PaymentRequestDto> captor = ArgumentCaptor.forClass(PaymentRequestDto.class);
        verify(paymentRequest).requestPayment(captor.capture());
        return captor.getValue();
    }

    private PaymentPaidEvent paidEvent(PaymentRequestDto request) {
        return PaymentPaidEvent.of(request.orderGroupId(), 7L, request.buyerId(), request.totalPrice(), paidAt);
    }
}
