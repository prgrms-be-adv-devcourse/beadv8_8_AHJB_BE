package com.jjinmak.recommend.in;

import com.jjinmak.contracts.auction.event.AuctionBidSucceedEvent;
import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.contracts.product.event.ProductWishChangedEvent;
import com.jjinmak.recommend.support.RecommendIntegrationTestSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Stream;

import static com.jjinmak.contracts.product.event.ProductChangeType.DELETED;
import static com.jjinmak.contracts.product.event.ProductWishChangeType.UNWISHED;
import static com.jjinmak.contracts.product.event.ProductWishChangeType.WISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

class KafkaEventSubscribingTest extends RecommendIntegrationTestSupport {

    static final LocalDateTime AT = LocalDateTime.of(2026, 11, 10, 10, 0, 30);

    @Autowired
    RecordMessageConverter kafkaMessageConverter;

    static Stream<Named<Object>> events() {
        return Stream.of(
                named("product.changed", new ProductChangedEvent(10L, DELETED, 3L, 20L, "듀얼센스", "ACCESSORY", "ETC", "직접 입력",
                        10000L, 50000L, AT.plusDays(2), "https://img.test/1.jpg", AT)),
                named("auction.started", new AuctionStartedEvent(100L, 55L, 10000L, 50000L, AT, AT.plusDays(3))),
                named("auction.won", new AuctionWonEvent(100L, 55L, 7L, 9L, 30000L, 3000L, AT.plusDays(1))),
                named("auction.failed", new AuctionFailedEvent(100L, 55L, "UNPAID", AT.plusDays(2))),
                named("auction.bid-succeed", new AuctionBidSucceedEvent(1L, 100L, 55L, 7L, 21000L, 4, AT.plusDays(3), AT.plusHours(1))),
                named("product.wish.changed (경매 시작 전)", new ProductWishChangedEvent(55L, 7L, WISHED, null, AT.minusHours(1))),
                named("product.wish.changed (경매 진행 중)", new ProductWishChangedEvent(55L, 7L, UNWISHED, 100L, AT.plusHours(1)))
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
