package com.jjinmak.recommend.in;

import com.jjinmak.contracts.auction.event.AuctionFailedEvent;
import com.jjinmak.contracts.auction.event.AuctionStartedEvent;
import com.jjinmak.contracts.auction.event.AuctionWonEvent;
import com.jjinmak.contracts.product.event.ProductChangedEvent;
import com.jjinmak.recommend.app.RecommendFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static com.jjinmak.contracts.product.event.ProductChangeType.CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RecommendKafkaListenerTest {

    static final LocalDateTime AT = LocalDateTime.of(2026, 11, 10, 10, 0);

    @Mock
    RecommendFacade recommendFacade;

    @InjectMocks
    RecommendKafkaListener recommendKafkaListener;

    static Stream<Arguments> subscriptions() {
        return Stream.of(
                arguments(ProductChangedEvent.class, "product.changed"),
                arguments(AuctionStartedEvent.class, "auction.started"),
                arguments(AuctionWonEvent.class, "auction.won"),
                arguments(AuctionFailedEvent.class, "auction.failed")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("subscriptions")
    @DisplayName("이벤트마다 그 이벤트의 토픽 하나만 recommend 그룹으로 구독한다")
    void handle_subscribesTopicWithRecommendGroup(Class<?> eventType, String topic) throws NoSuchMethodException {
        KafkaListener listener = RecommendKafkaListener.class.getMethod("handle", eventType).getAnnotation(KafkaListener.class);

        assertThat(listener.topics()).containsExactly(topic);
        assertThat(listener.groupId()).isEqualTo("recommend");
    }

    @Test
    @DisplayName("상품 변경 이벤트를 받으면 상품 복사본을 동기화한다")
    void productChanged_syncsProduct() {
        ProductChangedEvent event = new ProductChangedEvent(10L, CREATED, 0L, 20L, "듀얼센스", "ACCESSORY", "SONY", null,
                10000L, null, AT, null, AT);

        recommendKafkaListener.handle(event);

        verify(recommendFacade).syncProduct(event);
        verifyNoMoreInteractions(recommendFacade);
    }

    @Test
    @DisplayName("경매 시작 이벤트를 받으면 경매 복사본을 시작한다")
    void auctionStarted_startsAuction() {
        AuctionStartedEvent event = new AuctionStartedEvent(100L, 55L, 10000L, 50000L, AT, AT.plusDays(3));

        recommendKafkaListener.handle(event);

        verify(recommendFacade).startAuction(event);
        verifyNoMoreInteractions(recommendFacade);
    }

    @Test
    @DisplayName("낙찰 이벤트를 받으면 경매 복사본을 낙찰로 끝낸다")
    void auctionWon_winsAuction() {
        AuctionWonEvent event = new AuctionWonEvent(100L, 55L, 7L, 9L, 30000L, 3000L, AT);

        recommendKafkaListener.handle(event);

        verify(recommendFacade).winAuction(event);
        verifyNoMoreInteractions(recommendFacade);
    }

    @Test
    @DisplayName("유찰 이벤트를 받으면 경매 복사본을 유찰로 끝낸다")
    void auctionFailed_failsAuction() {
        AuctionFailedEvent event = new AuctionFailedEvent(100L, 55L, "NO_BID", AT);

        recommendKafkaListener.handle(event);

        verify(recommendFacade).failAuction(event);
        verifyNoMoreInteractions(recommendFacade);
    }
}
