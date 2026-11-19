package com.jjinmak.back.order.in;

import com.jayway.jsonpath.JsonPath;
import com.jjinmak.back.order.app.OrderFacade;
import com.jjinmak.back.order.domain.CartItem;
import com.jjinmak.back.order.domain.Order;
import com.jjinmak.back.order.domain.OrderMember;
import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.back.order.domain.OrderState;
import com.jjinmak.back.order.out.repository.CartItemRepository;
import com.jjinmak.back.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.order.out.repository.OrderOutboxRepository;
import com.jjinmak.back.order.out.repository.OrderRepository;
import com.jjinmak.back.payment.app.facade.PaymentFacade;
import com.jjinmak.back.payment.domain.Charge;
import com.jjinmak.back.payment.domain.Payment;
import com.jjinmak.back.payment.domain.Wallet;
import com.jjinmak.back.payment.domain.WalletMember;
import com.jjinmak.back.payment.domain.enums.ChargeStatus;
import com.jjinmak.back.payment.domain.enums.PaymentStatus;
import com.jjinmak.back.payment.domain.enums.WalletType;
import com.jjinmak.back.payment.out.dto.TossPaymentResponse;
import com.jjinmak.back.payment.out.repository.ChargeRepository;
import com.jjinmak.back.payment.out.repository.PaymentRepository;
import com.jjinmak.back.payment.out.repository.WalletMemberRepository;
import com.jjinmak.back.payment.out.repository.WalletRepository;
import com.jjinmak.back.payment.support.PaymentIntegrationTestSupport;
import com.jjinmak.common.exception.BusinessException;
import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.common.security.HeaderAuthenticationFilter;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.jjinmak.back.payment.exception.PaymentErrorCode.PAYMENT_CLOSED;
import static com.jjinmak.contracts.order.event.OrderEventTopics.ORDER_GROUP_DELETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderPaymentCloseIntegrationTest extends PaymentIntegrationTestSupport {

    private static final long WINNER_ID = 101L;
    private static final long SELLER_ID = 102L;

    private static final String TABLES = "reconciliation_mismatch, reconciliation_run, wallet_transactions, holding, "
            + "charge, withdrawal, payment_item, payment, toss_webhook_event, wallet, wallet_member, "
            + "orders, order_group, cart_item, order_outbox, order_won_auction, order_member";

    @MockitoBean
    OrderScheduler scheduledOrderScheduler;

    @Autowired WebApplicationContext context;
    @Autowired OrderFacade orderFacade;
    @Autowired OrderKafkaListener orderKafkaListener;
    @Autowired OrderMemberRepository orderMemberRepository;
    @Autowired CartItemRepository cartItemRepository;
    @MockitoSpyBean OrderGroupRepository orderGroupRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderOutboxRepository orderOutboxRepository;
    @Autowired PaymentFacade paymentFacade;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ChargeRepository chargeRepository;
    @Autowired WalletMemberRepository walletMemberRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @PersistenceContext EntityManager entityManager;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        resetDatabase();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new HeaderAuthenticationFilter())
                .build();

        OrderMember winner = createMember(WINNER_ID, "낙찰자");
        OrderMember seller = createMember(SELLER_ID, "판매자");
        cartItemRepository.save(new CartItem(winner, seller, 10L, 20L, 30_000L, 3_000L, LocalDateTime.now()));
        cartItemRepository.save(new CartItem(winner, seller, 11L, 21L, 40_000L, 3_000L, LocalDateTime.now()));
        cartItemRepository.save(new CartItem(winner, seller, 12L, 22L, 50_000L, 3_000L, LocalDateTime.now()));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        SecurityContextHolder.clearContext();
        resetDatabase();
    }

    @Test
    @DisplayName("결제창을 닫고 취소하면 204, 주문 그룹과 주문을 삭제하고 결제는 FAILED, 결제창은 ABORTED가 되며 장바구니 항목은 남는다. 같은 상품으로 다시 결제하면 새 주문 그룹으로 결제창이 열리고, 닫힌 옛 결제창 승인은 PAYMENT_CLOSED로 거절된다")
    void cancel_closedWindow_closesPaymentAndAllowsRetry() throws Exception {
        Window window = openWindow("[20, 21]");

        mockMvc.perform(cancel(window.groupId()))
                .andExpect(status().isNoContent());

        assertThat(orderGroupRepository.findById(window.groupId())).isEmpty();
        assertThat(orderRepository.count()).isZero();
        assertThat(paymentOf(window.groupId()).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(chargeStatusesOf(window.groupId())).containsExactly(ChargeStatus.ABORTED);
        assertThat(cartItemRepository.count()).isEqualTo(3);

        Window retry = openWindow("[20, 21]");

        assertThat(retry.groupId()).isNotEqualTo(window.groupId());
        assertThat(paymentOf(retry.groupId()).getStatus()).isEqualTo(PaymentStatus.CHARGING);
        assertThatThrownBy(() -> paymentFacade.confirmPayment(WINNER_ID, window.pgOrderId(), "pk-closed", window.amount()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PAYMENT_CLOSED);
    }

    @Test
    @DisplayName("결제를 닫은 뒤 주문 그룹 삭제가 실패하면 결제 닫기도 함께 되돌려, 결제는 CHARGING, 결제창은 REQUESTED로 남고 주문 그룹도 남는다")
    void cancel_deleteFails_rollsBackPaymentClose() throws Exception {
        Window window = openWindow("[20]");
        doThrow(new IllegalStateException("삭제 실패")).when(orderGroupRepository).delete(any());

        mockMvc.perform(cancel(window.groupId()))
                .andExpect(status().isInternalServerError());

        assertThat(orderGroupRepository.findById(window.groupId())).isPresent();
        assertThat(paymentOf(window.groupId()).getStatus()).isEqualTo(PaymentStatus.CHARGING);
        assertThat(chargeStatusesOf(window.groupId())).containsExactly(ChargeStatus.REQUESTED);
    }

    @Test
    @DisplayName("토스 승인 중에 결제창 취소가 오면 승인이 끝날 때까지 기다린 뒤 400 ORDER_STATE_BAD_REQUEST(ORDER003), 결제는 PAID이고 주문 그룹과 주문이 남아 결제 완료 이벤트로 PAID가 된다")
    void cancel_whileApproving_keepsGroupForPaidEvent() throws Exception {
        Window window = openWindow("[20]");
        Future<?> approval = approveSlowly(window, 1_000);

        mockMvc.perform(cancel(window.groupId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORDER003"));

        approval.get(10, TimeUnit.SECONDS);
        assertThat(orderGroupRepository.findById(window.groupId())).isPresent();
        assertThat(paymentOf(window.groupId()).getStatus()).isEqualTo(PaymentStatus.PAID);

        orderKafkaListener.handle(paidEventOf(window.groupId()));

        assertThat(orderRepository.findAll()).extracting(Order::getState).containsExactly(OrderState.PAID);
    }

    @Test
    @DisplayName("만료 스케줄러는 결제를 닫은 주문 그룹(결제창이 열려 있던 그룹)과 결제가 없는 주문 그룹만 삭제하고 그룹마다 주문 그룹 삭제 이벤트를 남긴다. 결제는 끝났지만 결제 완료 이벤트가 아직 처리되지 않은 주문 그룹은 남겨, 결제 완료 이벤트로 PAID가 된다")
    void expiry_deletesOnlyDiscardableGroups() throws Exception {
        Window open = openWindow("[20]");
        Window paid = openWindow("[21]");
        approve(paid);
        Long noPayment = orderFacade.createOrder(WINNER_ID, List.of(22L), "CARD", 0).orderGroupId();
        expireAllGroups();

        new OrderScheduler(orderFacade).deleteOverWaitingOrder();

        assertThat(orderGroupRepository.findById(open.groupId())).isEmpty();
        assertThat(paymentOf(open.groupId()).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(chargeStatusesOf(open.groupId())).containsExactly(ChargeStatus.ABORTED);
        assertThat(orderGroupRepository.findById(noPayment)).isEmpty();
        assertThat(orderGroupRepository.findById(paid.groupId())).isPresent();
        assertThat(paymentOf(paid.groupId()).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(orderOutboxRepository.findAll())
                .extracting(OrderOutbox::getTopic, OrderOutbox::getMessageKey)
                .containsExactlyInAnyOrder(
                        tuple(ORDER_GROUP_DELETED, String.valueOf(open.groupId())),
                        tuple(ORDER_GROUP_DELETED, String.valueOf(noPayment)));

        orderKafkaListener.handle(paidEventOf(paid.groupId()));

        assertThat(orderRepository.findAll())
                .extracting(Order::getAuctionId, Order::getState)
                .containsExactly(tuple(11L, OrderState.PAID));
        assertThat(orderFacade.findOverWaitingGroupIds()).isEmpty();
    }

    @Test
    @DisplayName("만료 스케줄러가 토스 승인 중인 결제의 주문 그룹을 만나면 락을 기다리다 그 그룹만 남기고(PAYMENT_IN_PROGRESS), 다른 만료 주문 그룹은 그대로 삭제한다")
    void expiry_approvalInProgress_keepsOnlyThatGroup() throws Exception {
        Window open = openWindow("[20]");
        Window approving = openWindow("[21]");
        Future<?> approval = approveSlowly(approving, 6_000);
        expireAllGroups();

        new OrderScheduler(orderFacade).deleteOverWaitingOrder();

        assertThat(orderGroupRepository.findById(open.groupId())).isEmpty();
        assertThat(paymentOf(open.groupId()).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderGroupRepository.findById(approving.groupId())).isPresent();

        approval.get(15, TimeUnit.SECONDS);
        assertThat(paymentOf(approving.groupId()).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    private record Window(Long groupId, String pgOrderId, long amount) {
    }

    private Window openWindow(String productIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cart/payment")
                        .header(AuthHeaders.MEMBER_ID, WINNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\": " + productIds + ", \"paymentMethod\": \"CARD\", \"pointUseAmount\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPaid").value(false))
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Window(
                ((Number) JsonPath.read(body, "$.data.orderGroupId")).longValue(),
                JsonPath.read(body, "$.data.data.pgOrderId"),
                ((Number) JsonPath.read(body, "$.data.data.chargeAmount")).longValue());
    }

    private void approve(Window window) {
        String paymentKey = "pk-" + window.pgOrderId();
        when(tossPaymentsClient.confirm(eq(paymentKey), eq(window.pgOrderId()), eq(window.amount()), anyString()))
                .thenReturn(approved(paymentKey, window));
        paymentFacade.confirmPayment(WINNER_ID, window.pgOrderId(), paymentKey, window.amount());
    }

    private Future<?> approveSlowly(Window window, long holdMillis) throws InterruptedException {
        String paymentKey = "pk-" + window.pgOrderId();
        CountDownLatch tossCalled = new CountDownLatch(1);
        when(tossPaymentsClient.confirm(eq(paymentKey), eq(window.pgOrderId()), eq(window.amount()), anyString()))
                .thenAnswer(invocation -> {
                    tossCalled.countDown();
                    Thread.sleep(holdMillis);
                    return approved(paymentKey, window);
                });
        Future<?> approval = executor.submit(() ->
                paymentFacade.confirmPayment(WINNER_ID, window.pgOrderId(), paymentKey, window.amount()));
        assertThat(tossCalled.await(5, TimeUnit.SECONDS)).isTrue();
        return approval;
    }

    private static TossPaymentResponse approved(String paymentKey, Window window) {
        return new TossPaymentResponse(paymentKey, window.pgOrderId(), "찐막 주문 결제", "DONE",
                window.amount(), window.amount(), "카드", null, "2026-11-19T10:00:00+09:00", null, null, null);
    }

    private MockHttpServletRequestBuilder cancel(Long groupId) {
        return post("/api/v1/cart")
                .header(AuthHeaders.MEMBER_ID, WINNER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderGroupId\": " + groupId + "}");
    }

    private Payment paymentOf(Long groupId) {
        return paymentRepository.findByOrderGroupId(groupId).orElseThrow();
    }

    private List<ChargeStatus> chargeStatusesOf(Long groupId) {
        return chargeRepository.findByPaymentOrderByIdDesc(paymentOf(groupId)).stream()
                .map(Charge::getStatus)
                .toList();
    }

    private PaymentPaidEvent paidEventOf(Long groupId) {
        Payment payment = paymentOf(groupId);
        return PaymentPaidEvent.of(payment.getOrderGroupId(), payment.getId(), payment.getHolderId(),
                payment.getTotalPrice(), payment.getPaidAt());
    }

    private void expireAllGroups() {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("update OrderGroup g set g.lifeTime = :lifeTime")
                .setParameter("lifeTime", LocalDateTime.now().minusMinutes(1))
                .executeUpdate());
    }

    private OrderMember createMember(long memberId, String name) {
        transactionTemplate.executeWithoutResult(status -> {
            WalletMember walletMember = walletMemberRepository.save(new WalletMember(memberId, UUID.randomUUID()));
            walletRepository.save(Wallet.createUserWallet(walletMember));
        });
        return orderMemberRepository.save(new OrderMember(memberId, UUID.randomUUID(), name));
    }

    private void resetDatabase() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("truncate table " + TABLES + " restart identity cascade").executeUpdate();
            walletRepository.save(Wallet.createSystemWallet(WalletType.FEE));
            walletRepository.save(Wallet.createSystemWallet(WalletType.ESCROW));
        });
    }
}
