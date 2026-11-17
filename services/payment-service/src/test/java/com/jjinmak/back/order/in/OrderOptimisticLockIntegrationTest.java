package com.jjinmak.back.order.in;

import com.jjinmak.common.security.AuthHeaders;
import com.jjinmak.common.security.HeaderAuthenticationFilter;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.order.app.OrderConfirmPurchaseUseCase;
import com.jjinmak.back.order.app.OrderFacade;
import com.jjinmak.back.order.domain.Order;
import com.jjinmak.back.order.domain.OrderGroup;
import com.jjinmak.back.order.domain.OrderMember;
import com.jjinmak.back.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.order.out.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.jjinmak.back.order.domain.OrderState.CONFIRMED;
import static com.jjinmak.back.order.domain.OrderState.PAID;
import static com.jjinmak.back.order.domain.OrderState.REFUND_REQUESTED;
import static com.jjinmak.back.order.domain.OrderState.SHIPPING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:optimisticlock;MODE=PostgreSQL")
class OrderOptimisticLockIntegrationTest {

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoSpyBean
    OrderConfirmPurchaseUseCase orderConfirmPurchaseUseCase;

    @Autowired
    WebApplicationContext context;

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
    TransactionTemplate transactionTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    MockMvc mockMvc;

    TransactionTemplate newTransaction;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new HeaderAuthenticationFilter())
                .build();

        newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        orderRepository.deleteAllInBatch();
        orderGroupRepository.deleteAllInBatch();
        orderMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("구매확정 두 건이 겹치면 나중에 커밋하는 쪽이 버전 충돌로 롤백되고, 구매확정 이벤트는 한 번만 나간다")
    void confirmPurchaseTwice_laterCommitRollsBack() {
        Long orderId = saveShippedOrder();

        assertThatThrownBy(() -> commitBetweenReadAndWrite(orderId,
                () -> orderFacade.confirmPurchase(1L, orderId),
                () -> orderFacade.confirmPurchase(1L, orderId)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(CONFIRMED);
        assertThat(order.getVersion()).isEqualTo(1L);
        verify(kafkaTemplate, times(1)).send(eq("order.confirm-purchase"), eq(String.valueOf(orderId)), any());
    }

    @Test
    @DisplayName("환불 요청을 처리하는 사이 자동 구매확정이 먼저 커밋되면 환불 요청은 롤백되고, 환불 요청 이벤트는 나가지 않는다")
    void requestRefund_autoConfirmCommittedFirst_rollsBack() {
        Long orderId = saveShippedOrder();

        assertThatThrownBy(() -> commitBetweenReadAndWrite(orderId,
                () -> orderFacade.confirmPurchaseAuto(orderId),
                () -> orderFacade.requestRefund(1L, orderId)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(CONFIRMED);
        verify(kafkaTemplate, times(1)).send(eq("order.confirm-purchase"), eq(String.valueOf(orderId)), any());
        verify(kafkaTemplate, never()).send(eq("order.refund-requested"), anyString(), any());
    }

    @Test
    @DisplayName("구매확정 알림을 처리하는 사이 구매확정이 먼저 커밋되면 알림은 롤백되고, 주문은 CONFIRMED로 남는다")
    void sendAutoConfirmReminder_confirmCommittedFirst_keepsConfirmed() {
        Long orderId = saveShippedOrder();

        assertThatThrownBy(() -> commitBetweenReadAndWrite(orderId,
                () -> orderFacade.confirmPurchase(1L, orderId),
                () -> orderFacade.sendAutoConfirmReminder(orderId)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getState()).isEqualTo(CONFIRMED);
        assertThat(order.getAutoConfirmAlarmed()).isFalse();
        verify(kafkaTemplate, never()).send(eq("order.remind-auto-confirm"), anyString(), any());
    }

    @Test
    @DisplayName("배송 생성 이벤트를 처리하는 사이 시스템 환불이 먼저 커밋되면 배송 시작은 롤백되고, 주문은 REFUND_REQUESTED로 남는다")
    void startDelivery_systemRefundCommittedFirst_rollsBack() {
        Long orderId = savePaidOrder();

        assertThatThrownBy(() -> commitBetweenReadAndWrite(orderId,
                () -> orderFacade.requestSystemRefund(orderId),
                () -> orderKafkaListener.handle(new DeliveryCreatedEvent(orderId))))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(REFUND_REQUESTED);
        verify(kafkaTemplate, times(1)).send(eq("order.refund-requested"), eq(String.valueOf(orderId)), any());
    }

    @Test
    @DisplayName("구매확정 API를 처리하는 사이 자동 구매확정이 먼저 커밋되면 409 COMMON005로 응답하고, 구매확정 이벤트는 자동 구매확정의 한 번만 나간다")
    void confirmPurchaseApi_autoConfirmCommittedFirst_returnsConflict() throws Exception {
        Long orderId = saveShippedOrder();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            newTransaction.executeWithoutResult(status -> orderFacade.confirmPurchaseAuto(orderId));
            return null;
        }).when(orderConfirmPurchaseUseCase).confirmPurchase(1L, orderId);

        mockMvc.perform(post("/api/v1/order/{orderId}/confirm-purchase", orderId)
                        .header(AuthHeaders.MEMBER_ID, 1L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMMON005"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getState()).isEqualTo(CONFIRMED);
        verify(kafkaTemplate, times(1)).send(eq("order.confirm-purchase"), eq(String.valueOf(orderId)), any());
    }

    private void commitBetweenReadAndWrite(Long orderId, Runnable committedFirst, Runnable writtenLater) {
        transactionTemplate.executeWithoutResult(status -> {
            orderRepository.findById(orderId).orElseThrow();
            newTransaction.executeWithoutResult(inner -> committedFirst.run());
            writtenLater.run();
        });
    }

    private Long saveShippedOrder() {
        Order order = newOrder();
        order.paid(LocalDateTime.now());
        order.validateAndUpdateState(PAID, SHIPPING);
        order.shipped(LocalDateTime.now());
        return orderRepository.save(order).getId();
    }

    private Long savePaidOrder() {
        Order order = newOrder();
        order.paid(LocalDateTime.now());
        return orderRepository.save(order).getId();
    }

    private Order newOrder() {
        OrderMember winner = orderMemberRepository.save(new OrderMember(1L, UUID.randomUUID(), "낙찰자"));
        OrderMember seller = orderMemberRepository.save(new OrderMember(2L, UUID.randomUUID(), "판매자"));
        OrderGroup group = orderGroupRepository.save(new OrderGroup(winner, 33_000L));
        return new Order(group, winner, seller, 10L, 20L, 30_000L, 3_000L);
    }
}
