package com.jjinmak.notification.in;

import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.delivery.domain.CarrierType;
import com.jjinmak.contracts.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
import com.jjinmak.notification.app.NotificationCreateUseCase;
import com.jjinmak.notification.app.mapper.OrderNotificationMapper;
import com.jjinmak.notification.domain.Notification;
import com.jjinmak.notification.out.NotificationRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;
import static com.jjinmak.contracts.order.event.OrderEventTopics.ORDER_PAYMENT_SUCCEED;
import static com.jjinmak.contracts.order.event.OrderEventTopics.ORDER_SEND_PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Import(NotificationKafkaIntegrationTest.Containers.class)
class NotificationKafkaIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 11, 15, 0);

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }

        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer("apache/kafka:4.3.2");
        }
    }

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired KafkaContainer kafka;
    @Autowired NotificationRepository notificationRepository;

    @MockitoSpyBean NotificationCreateUseCase notificationCreateUseCase;
    @MockitoSpyBean OrderNotificationMapper orderNotificationMapper;

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
    }

    @Test
    void 다른_서비스가_발행한_낙찰_이벤트를_받아_알림_2건을_저장한다() throws Exception {
        발행한다(AUCTION_WON, "12", new AuctionWonEvent(12L, 3L, 7L, 9L, 50_000L, 3_000L, AT));

        기다린다(() -> 경매_알림(12L).size() == 2);
        assertThat(경매_알림(12L))
                .extracting(Notification::getReceiverId)
                .containsExactlyInAnyOrder(7L, 9L);
    }

    @Test
    void 같은_이벤트를_두_번_받아도_알림은_늘어나지_않는다() throws Exception {
        AuctionWonEvent event = new AuctionWonEvent(13L, 3L, 7L, 9L, 50_000L, 3_000L, AT);

        발행한다(AUCTION_WON, "13", event);
        발행한다(AUCTION_WON, "13", event);

        기다린다(() -> {
            try {
                then(notificationCreateUseCase).should(times(2)).create(anyList(), any());
                return true;
            } catch (AssertionError e) {
                return false;
            }
        });
        assertThat(경매_알림(13L)).hasSize(2);
    }

    @Test
    void 받는_회원이_없는_이벤트는_재시도_없이_DLT로_간다() throws Exception {
        발행한다(ORDER_PAYMENT_SUCCEED, "201", new OrderPaymentSucceedEvent(201L, 21L, 6L, 7L, null, AT));

        ConsumerRecord<String, byte[]> dlt = DLT에서_찾는다(ORDER_PAYMENT_SUCCEED + "-dlt", "201");

        assertThat(헤더_전체(dlt)).contains("InvalidEventException");
        then(orderNotificationMapper).should(times(1)).fromPaymentSucceed(any());
    }

    @Test
    void 일시적인_오류는_3번_재시도한_뒤_DLT로_간다() throws Exception {
        doThrow(new IllegalStateException("저장 일시 장애"))
                .when(notificationCreateUseCase).create(anyList(), any());

        발행한다(ORDER_SEND_PRODUCT, "301", new OrderSendProductEvent(301L, 7L, 9L, CarrierType.CJ, "123456789"));

        DLT에서_찾는다(ORDER_SEND_PRODUCT + "-dlt", "301");
        then(notificationCreateUseCase).should(times(4)).create(anyList(), any());
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void 다른_구독_그룹도_같은_낙찰_이벤트를_따로_받는다() throws Exception {
        try (KafkaConsumer<String, byte[]> orderService = 컨슈머("order-service-" + UUID.randomUUID())) {
            orderService.subscribe(List.of(AUCTION_WON));

            발행한다(AUCTION_WON, "14", new AuctionWonEvent(14L, 3L, 7L, 9L, 50_000L, 3_000L, AT));

            assertThat(찾는다(orderService, "14")).isPresent();
            기다린다(() -> 경매_알림(14L).size() == 2);
        }
    }

    private void 발행한다(String topic, String key, Object event) throws Exception {
        kafkaTemplate.send(topic, key, event).get();
    }

    private List<Notification> 경매_알림(Long auctionId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getDedupeKey().contains(":auction-" + auctionId + ":"))
                .toList();
    }

    private ConsumerRecord<String, byte[]> DLT에서_찾는다(String dltTopic, String key) {
        try (KafkaConsumer<String, byte[]> consumer = 컨슈머("dlt-reader-" + UUID.randomUUID())) {
            consumer.subscribe(List.of(dltTopic));
            return 찾는다(consumer, key).orElseThrow(() -> new AssertionError(dltTopic + "에 key=" + key + " 메시지가 없습니다"));
        }
    }

    private Optional<ConsumerRecord<String, byte[]>> 찾는다(KafkaConsumer<String, byte[]> consumer, String key) {
        long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                if (key.equals(record.key())) {
                    return Optional.of(record);
                }
            }
        }
        return Optional.empty();
    }

    private KafkaConsumer<String, byte[]> 컨슈머(String groupId) {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class
        ));
    }

    private String 헤더_전체(ConsumerRecord<String, byte[]> record) {
        StringBuilder all = new StringBuilder();
        for (Header header : record.headers()) {
            all.append(header.key()).append('=').append(new String(header.value(), StandardCharsets.UTF_8)).append('\n');
        }
        return all.toString();
    }

    private void 기다린다(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("시간 안에 조건을 만족하지 않았습니다");
    }
}
