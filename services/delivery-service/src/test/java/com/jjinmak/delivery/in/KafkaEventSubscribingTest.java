package com.jjinmak.delivery.in;

import com.jjinmak.contracts.member.dto.MemberDto;
import com.jjinmak.contracts.member.event.MemberCreatedEvent;
import com.jjinmak.contracts.order.event.OrderSendProductEvent;
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

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.jjinmak.contracts.delivery.domain.CarrierType.CJ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

@SpringBootTest
class KafkaEventSubscribingTest {

    @Autowired
    RecordMessageConverter kafkaMessageConverter;

    static Stream<Named<Object>> events() {
        return Stream.of(
                named("member.created", new MemberCreatedEvent(new MemberDto(2L, UUID.randomUUID(), "판매자"))),
                named("order.send-product", new OrderSendProductEvent(100L, 1L, 2L, CJ, "1234567890"))
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
