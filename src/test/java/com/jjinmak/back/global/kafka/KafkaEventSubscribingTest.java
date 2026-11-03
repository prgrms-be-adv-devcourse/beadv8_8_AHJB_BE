package com.jjinmak.back.global.kafka;

import com.jjinmak.back.boundedContext.product.app.ProductFacade;
import com.jjinmak.back.boundedContext.review.app.ReviewFacade;
import com.jjinmak.back.boundedContext.settlement.app.SettlementFacade;
import com.jjinmak.back.shared.auction.event.AuctionFailedEvent;
import com.jjinmak.back.shared.auction.event.AuctionStartedEvent;
import com.jjinmak.back.shared.auction.event.AuctionWonEvent;
import com.jjinmak.back.shared.order.event.OrderConfirmPurchaseEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

// 다른 컨텍스트가 발행한 JSON이 토픽을 거쳐 구독 컨텍스트의 이벤트 record로 복원되는지 확인한다.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kafkasubscribing",
        "spring.kafka.admin.auto-create=true",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
        "spring.kafka.producer.properties[spring.json.add.type.headers]=false",
        "spring.kafka.producer.properties[max.block.ms]=10000",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer"
})
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class KafkaEventSubscribingTest {

    static final long TIMEOUT_MS = 10_000L;

    @MockitoBean
    ProductFacade productFacade;

    @MockitoBean
    ReviewFacade reviewFacade;

    @MockitoBean
    SettlementFacade settlementFacade;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    LocalDateTime at = LocalDateTime.of(2026, 11, 3, 12, 0);

    @Test
    @DisplayName("상품 컨텍스트는 auction.started 토픽의 경매 시작 이벤트를 받는다")
    void product_receivesAuctionStarted() {
        AuctionStartedEvent event = new AuctionStartedEvent(1L, 2L, 10_000L, 50_000L, at, at.plusDays(3));

        kafkaTemplate.send("auction.started", "1", event);

        verify(productFacade, timeout(TIMEOUT_MS)).startAuction(event);
    }

    @Test
    @DisplayName("상품 컨텍스트는 auction.won 토픽의 낙찰 이벤트를 받는다")
    void product_receivesAuctionWon() {
        AuctionWonEvent event = new AuctionWonEvent(1L, 2L, 3L, 4L, 30_000L, 3_000L, at);

        kafkaTemplate.send("auction.won", "1", event);

        verify(productFacade, timeout(TIMEOUT_MS)).markAuctionSold(event);
    }

    @Test
    @DisplayName("상품 컨텍스트는 auction.failed 토픽의 무산 이벤트를 받는다")
    void product_receivesAuctionFailed() {
        AuctionFailedEvent event = new AuctionFailedEvent(1L, 2L, "NO_BID", at);

        kafkaTemplate.send("auction.failed", "1", event);

        verify(productFacade, timeout(TIMEOUT_MS)).markAuctionFailed(event);
    }

    @Test
    @DisplayName("후기 컨텍스트는 order.confirm-purchase 토픽의 구매확정 이벤트를 받는다")
    void review_receivesOrderConfirmPurchase() {
        OrderConfirmPurchaseEvent event = new OrderConfirmPurchaseEvent(1L, 2L, 3L, 4L, 5L, 30_000L, 3_000L, at);

        kafkaTemplate.send("order.confirm-purchase", "1", event);

        verify(reviewFacade, timeout(TIMEOUT_MS)).createReviewableOrder(event);
    }
}
