package com.jjinmak.order.in;

import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.delivery.event.DeliveryCreatedEvent;
import com.jjinmak.contracts.delivery.event.DeliveryStatusUpdatedEvent;
import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.payment.event.PaymentPaidEvent;
import com.jjinmak.contracts.refund.dto.RefundDto;
import com.jjinmak.contracts.refund.event.RefundAcceptedEvent;
import com.jjinmak.contracts.refund.event.RefundRejectedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.DELIVERED;
import static com.jjinmak.contracts.delivery.domain.DeliveryStatusCode.IN_TRANSIT;
import static com.jjinmak.contracts.refund.domain.RefundStatus.ACCEPTED;
import static com.jjinmak.contracts.refund.domain.RefundStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

@SpringBootTest
class KafkaEventSubscribingTest {

    @Autowired
    RecordMessageConverter kafkaMessageConverter;

    static Stream<Named<Object>> events() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 6, 12, 0, 30);
        return Stream.of(
                named("auction.won", new AuctionWonEvent(10L, 20L, 1L, 2L, 30_000L, 3_000L, at)),
                named("member.created", new MemberCreatedEvent(new MemberDto(1L, UUID.randomUUID(), "낙찰자"))),
                named("delivery.created", new DeliveryCreatedEvent(100L)),
                named("delivery.status-updated", new DeliveryStatusUpdatedEvent(100L, 1L, 2L, IN_TRANSIT, DELIVERED, at)),
                named("payment.payment-paid", PaymentPaidEvent.of(50L, 7L, 1L, 87_000L, at)),
                named("refund.accepted", RefundAcceptedEvent.of(new RefundDto(7L, 100L, 1L, ACCEPTED, 33_000L, at, at.plusDays(1)))),
                named("refund.rejected", RefundRejectedEvent.of(new RefundDto(8L, 101L, 1L, REJECTED, 33_000L, at, at.plusDays(1))))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("다른 서비스가 타입 헤더 없이 JSON으로 보낸 이벤트는 구독 메서드의 이벤트 타입으로 같은 값이 복원된다")
    void producedJson_isConvertedToSameEvent(Object event) {
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("topic", 0, 0L, "1", serialized(event));

        Message<?> message = kafkaMessageConverter.toMessage(record, null, null, event.getClass());

        assertThat(message.getPayload()).isEqualTo(event);
    }

    private byte[] serialized(Object event) {
        try (JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>()) {
            serializer.configure(Map.of(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false), false);
            return serializer.serialize("topic", event);
        }
    }
}
