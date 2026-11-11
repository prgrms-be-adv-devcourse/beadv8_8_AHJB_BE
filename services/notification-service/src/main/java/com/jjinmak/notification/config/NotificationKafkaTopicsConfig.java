package com.jjinmak.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.List;
import java.util.stream.Stream;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_FAILED;
import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;
import static com.jjinmak.contracts.delivery.event.DeliveryEventTopics.DELIVERY_STATUS_UPDATED;
import static com.jjinmak.contracts.order.event.OrderEventTopics.*;
import static com.jjinmak.contracts.payment.event.PaymentEventTopics.PAYMENT_RELEASED_EVENT;
import static com.jjinmak.contracts.refund.event.RefundEventTopics.REFUND_COMPLETED;
import static com.jjinmak.contracts.refund.event.RefundEventTopics.REFUND_REJECTED;

@Configuration
public class NotificationKafkaTopicsConfig {

    private static final String DLT = "-dlt";

    static final List<String> SUBSCRIBED_TOPICS = List.of(
            AUCTION_WON, AUCTION_FAILED,
            CART_PAYMENT_DEFAULT, ORDER_REMIND_PAYMENT_DEFAULT,
            ORDER_PAYMENT_SUCCEED, ORDER_SEND_PRODUCT, ORDER_CONFIRM_PURCHASE,
            ORDER_REMIND_AUTO_CONFIRM, ORDER_REFUND_REQUESTED,
            DELIVERY_STATUS_UPDATED,
            REFUND_COMPLETED, REFUND_REJECTED,
            PAYMENT_RELEASED_EVENT
    );

    @Bean
    KafkaAdmin.NewTopics notificationTopics() {
        return new KafkaAdmin.NewTopics(SUBSCRIBED_TOPICS.stream()
                .flatMap(topic -> Stream.of(topic, topic + DLT))
                .map(name -> TopicBuilder.name(name).build())
                .toArray(org.apache.kafka.clients.admin.NewTopic[]::new));
    }
}