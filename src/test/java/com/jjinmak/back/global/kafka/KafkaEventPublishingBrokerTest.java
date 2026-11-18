package com.jjinmak.back.global.kafka;

import com.jjinmak.back.boundedContext.auction.in.AuctionEventListener;
import com.jjinmak.back.boundedContext.delivery.in.DeliveryEventListener;
import com.jjinmak.back.boundedContext.order.in.OrderEventListener;
import com.jjinmak.back.boundedContext.payment.in.PaymentEventListener;
import com.jjinmak.back.boundedContext.refund.in.RefundEventListener;
import com.jjinmak.back.boundedContext.review.in.ReviewEventListener;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.cart.event.CartPaymentDefaultEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.back.shared.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import com.jjinmak.back.shared.order.event.OrderGroupDeletedEvent;
import com.jjinmak.back.shared.order.event.OrderPaymentSucceedEvent;
import com.jjinmak.back.shared.order.event.OrderRefundAcceptedEvent;
import com.jjinmak.back.shared.order.event.OrderRefundRequestedEvent;
import com.jjinmak.back.shared.order.event.OrderSendProductEvent;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jjinmak.back.shared.delivery.domain.CarrierType.CJ;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.back.shared.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kafkabroker",
        "spring.kafka.admin.auto-create=true",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
        "spring.kafka.producer.acks=all",
        "spring.kafka.producer.properties[enable.idempotence]=true",
        "spring.kafka.producer.properties[spring.json.add.type.headers]=false",
        "spring.kafka.producer.properties[max.block.ms]=10000"
})
@DirtiesContext
class KafkaEventPublishingBrokerTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @MockitoBean
    AuctionEventListener auctionEventListener;

    @MockitoBean
    PaymentEventListener paymentEventListener;

    @MockitoBean
    SettlementEventListener settlementEventListener;

    @MockitoBean
    ReviewEventListener reviewEventListener;

    @MockitoBean
    RefundEventListener refundEventListener;

    @MockitoBean
    DeliveryEventListener deliveryEventListener;

    @MockitoBean
    OrderEventListener orderEventListener;

    @Autowired
    EventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    List<String> eventTopics = List.of(
            "order.confirm-purchase", "order.refund-requested", "order.group-deleted", "order.payment-succeed",
            "order.refund-accepted", "order.send-product", "order.cart-payment-default",
            "delivery.created", "delivery.status-updated"
    );

    LocalDateTime at = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Test
    @DisplayName("토픽 자동 생성을 끈 브로커에, 앱이 선언한 주문·배송 토픽과 DLT가 만들어진다")
    void declaredTopics_areCreatedOnBroker() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            Set<String> topics = admin.listTopics().names().get();

            assertThat(topics).containsAll(eventTopics);
            assertThat(topics).containsAll(eventTopics.stream().map(topic -> topic + "-dlt").toList());
        }
    }

    @Test
    @DisplayName("커밋한 이벤트는 키와 타입 헤더 없는 JSON 값으로 토픽에 들어가고, 롤백한 이벤트는 들어가지 않는다")
    void committedEvents_areStoredAsJsonRecords() {
        commit(new OrderConfirmPurchaseEvent(1L, 2L, 3L, 4L, 5L, 30_000L, 3_000L, at));
        commit(OrderRefundRequestedEvent.byWinner(1L, 2L, 3L, 5L, 30_000L, 3_000L));
        commit(new OrderGroupDeletedEvent(7L, 33_000L));
        commit(new OrderPaymentSucceedEvent(1L, 4L, 5L, at));
        commit(new OrderRefundAcceptedEvent(1L, 4L));
        commit(new OrderSendProductEvent(1L, 2L, 3L, CJ, "1234567890"));
        commit(new CartPaymentDefaultEvent(4L, 5L, 2L, 3L, 30_000L, 3_000L));
        commit(new DeliveryCreatedEvent(1L));
        commit(new DeliveryStatusUpdatedEvent(1L, IN_TRANSIT, DELIVERED, at));
        rollBack(new OrderConfirmPurchaseEvent(99L, 2L, 3L, 4L, 5L, 30_000L, 3_000L, at));
        kafkaTemplate.flush();

        List<ConsumerRecord<String, String>> records = readAll(9);

        assertThat(records)
                .extracting(ConsumerRecord::topic, ConsumerRecord::key, ConsumerRecord::value)
                .containsExactlyInAnyOrder(
                        tuple("order.confirm-purchase", "1", """
                                {"orderId":1,"winnerId":2,"sellerId":3,"auctionId":4,"productId":5,"winningPrice":30000,"deliveryFee":3000,"confirmedAt":"2026-10-30T12:00:00"}"""),
                        tuple("order.refund-requested", "1", """
                                {"orderId":1,"winnerId":2,"sellerId":3,"productId":5,"winningPrice":30000,"deliveryFee":3000,"refundType":"WINNER_REQUEST"}"""),
                        tuple("order.group-deleted", "7", """
                                {"orderGroupId":7,"totalPrice":33000}"""),
                        tuple("order.payment-succeed", "1", """
                                {"orderId":1,"auctionId":4,"productId":5,"paidAt":"2026-10-30T12:00:00"}"""),
                        tuple("order.refund-accepted", "1", """
                                {"orderId":1,"auctionId":4}"""),
                        tuple("order.send-product", "1", """
                                {"orderId":1,"winnerId":2,"sellerId":3,"carrier":"CJ","waybill":"1234567890"}"""),
                        tuple("order.cart-payment-default", "4", """
                                {"auctionId":4,"productId":5,"winnerId":2,"sellerId":3,"winningPrice":30000,"deliveryFee":3000}"""),
                        tuple("delivery.created", "1", """
                                {"orderId":1}"""),
                        tuple("delivery.status-updated", "1", """
                                {"orderId":1,"beforeStatus":"IN_TRANSIT","afterStatus":"DELIVERED","updatedAt":"2026-10-30T12:00:00"}""")
                );
        assertThat(records).allSatisfy(record -> assertThat(record.headers().toArray()).isEmpty());
    }

    private void commit(Object event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private void rollBack(Object event) {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(event);
            status.setRollbackOnly();
        });
    }

    private List<ConsumerRecord<String, String>> readAll(int expected) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            List<TopicPartition> partitions = eventTopics.stream()
                    .flatMap(topic -> consumer.partitionsFor(topic).stream())
                    .map(partition -> new TopicPartition(partition.topic(), partition.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);

            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                polled.forEach(records::add);
                if (records.size() >= expected && polled.isEmpty()) {
                    break;
                }
            }
            return records;
        }
    }
}
