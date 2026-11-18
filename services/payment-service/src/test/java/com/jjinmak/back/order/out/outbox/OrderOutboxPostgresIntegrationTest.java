package com.jjinmak.back.order.out.outbox;

import com.jjinmak.back.order.app.OrderFacade;
import com.jjinmak.back.order.domain.Order;
import com.jjinmak.back.order.domain.OrderGroup;
import com.jjinmak.back.order.domain.OrderMember;
import com.jjinmak.back.order.domain.OrderOutbox;
import com.jjinmak.back.order.in.OrderScheduler;
import com.jjinmak.back.order.out.repository.OrderGroupRepository;
import com.jjinmak.back.order.out.repository.OrderMemberRepository;
import com.jjinmak.back.order.out.repository.OrderOutboxRepository;
import com.jjinmak.back.order.out.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(OrderOutboxPostgresIntegrationTest.PostgresContainerConfig.class)
class OrderOutboxPostgresIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresContainerConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }
    }

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    OrderScheduler scheduledOrderScheduler;

    @Autowired
    OrderFacade orderFacade;

    @Autowired
    OrderMemberRepository orderMemberRepository;

    @Autowired
    OrderGroupRepository orderGroupRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderOutboxRepository orderOutboxRepository;

    @AfterEach
    void tearDown() {
        orderOutboxRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        orderGroupRepository.deleteAllInBatch();
        orderMemberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("PostgreSQL에서 판매자가 송장을 등록하면 발송 요청 이벤트가 아웃박스에 저장된다")
    void requestDelivery_savesSendProductOutbox() {
        Long orderId = savePaidOrder();

        orderFacade.requestDelivery(2L, orderId, CJ, "1234567890");

        List<OrderOutbox> saved = orderOutboxRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getTopic()).isEqualTo("order.send-product");
        assertThat(saved.getFirst().getMessageKey()).isEqualTo(String.valueOf(orderId));
        assertThat(saved.getFirst().getPublishedAt()).isNull();
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
