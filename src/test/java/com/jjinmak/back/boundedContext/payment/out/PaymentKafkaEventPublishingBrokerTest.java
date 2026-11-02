package com.jjinmak.back.boundedContext.payment.out;

import com.jjinmak.back.boundedContext.auction.in.AuctionEventListener;
import com.jjinmak.back.boundedContext.delivery.in.DeliveryEventListener;
import com.jjinmak.back.boundedContext.order.in.OrderEventListener;
import com.jjinmak.back.boundedContext.payment.in.PaymentEventListener;
import com.jjinmak.back.boundedContext.refund.in.RefundEventListener;
import com.jjinmak.back.boundedContext.review.in.ReviewEventListener;
import com.jjinmak.back.boundedContext.settlement.in.SettlementEventListener;
import com.jjinmak.back.global.eventPublisher.EventPublisher;
import com.jjinmak.back.shared.payment.event.PaymentPaidEvent;
import com.jjinmak.back.shared.payment.event.WalletRefundedEvent;
import com.jjinmak.back.shared.payment.event.WalletReleasedEvent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 결제 이벤트가 실제 브로커의 토픽에 들어가는지 (Testcontainers Kafka).
 * 테스트 application.yaml 이 Kafka 없이 돌도록 되어 있어, 메인의 producer 설정과 토픽 자동 생성을 여기서 덮어쓴다.
 * Docker 가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paymentkafkabroker",
        "spring.kafka.admin.auto-create=true",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
        "spring.kafka.producer.acks=all",
        "spring.kafka.producer.properties[enable.idempotence]=true",
        "spring.kafka.producer.properties[spring.json.add.type.headers]=false",
        "spring.kafka.producer.properties[max.block.ms]=10000"
})
@DirtiesContext
class PaymentKafkaEventPublishingBrokerTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    // 결제 이벤트를 Spring 이벤트로도 받는 다른 컨텍스트 리스너는 전부 모킹 (DB 데이터 없이 이벤트만 발행하므로)
    @MockitoBean AuctionEventListener auctionEventListener;
    @MockitoBean PaymentEventListener paymentEventListener;
    @MockitoBean SettlementEventListener settlementEventListener;
    @MockitoBean ReviewEventListener reviewEventListener;
    @MockitoBean RefundEventListener refundEventListener;
    @MockitoBean DeliveryEventListener deliveryEventListener;
    @MockitoBean OrderEventListener orderEventListener;

    @Autowired EventPublisher eventPublisher;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired KafkaTemplate<String, Object> kafkaTemplate;

    List<String> paymentTopics = List.of("payment.payment-paid", "payment.payment-refund", "payment.payment-released");

    LocalDateTime at = LocalDateTime.of(2026, 11, 2, 12, 0);

    @Test
    @DisplayName("토픽 자동 생성을 끈 브로커에, 결제 토픽 세 개와 각 DLT가 만들어진다")
    void paymentTopicsAndDlts_areCreatedOnBroker() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            Set<String> topics = admin.listTopics().names().get();

            assertThat(topics).containsAll(paymentTopics);
            assertThat(topics).containsAll(paymentTopics.stream().map(topic -> topic + "-dlt").toList());
        }
    }

    @Test
    @DisplayName("커밋한 결제 이벤트는 키와 타입 헤더 없는 JSON 으로 토픽에 들어가고, 롤백한 이벤트는 들어가지 않는다")
    void committedPaymentEvents_areStoredAsJsonRecords() {
        commit(new PaymentPaidEvent("evt-1", at, 7L, 11L, 2L, 30_000L, at));
        commit(new WalletRefundedEvent(501L, 81L, 11L, 10_000L, at));
        commit(new WalletReleasedEvent(61L, 3L, 10_000L, 1_000L, 9_000L, at));
        rollBack(new PaymentPaidEvent("evt-rollback", at, 99L, 12L, 2L, 5_000L, at));
        kafkaTemplate.flush();

        List<ConsumerRecord<String, String>> records = readAll(3);

        assertThat(records)
                .extracting(ConsumerRecord::topic, ConsumerRecord::key, ConsumerRecord::value)
                .containsExactlyInAnyOrder(
                        tuple("payment.payment-paid", "7", """
                                {"eventId":"evt-1","occurredAt":"2026-11-02T12:00:00","orderGroupId":7,"paymentId":11,"buyerId":2,"totalPrice":30000,"paidAt":"2026-11-02T12:00:00"}"""),
                        tuple("payment.payment-refund", "81", """
                                {"refundId":501,"orderId":81,"paymentId":11,"amount":10000,"refundedAt":"2026-11-02T12:00:00"}"""),
                        tuple("payment.payment-released", "61", """
                                {"orderId":61,"sellerId":3,"amount":10000,"feeAmount":1000,"settlementAmount":9000,"releasedAt":"2026-11-02T12:00:00"}""")
                );
        assertThat(records).noneMatch(record -> record.key().equals("99"));
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
            List<TopicPartition> partitions = paymentTopics.stream()
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
