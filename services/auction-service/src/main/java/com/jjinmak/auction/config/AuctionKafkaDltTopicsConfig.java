package com.jjinmak.auction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.contracts.order.event.OrderEventTopics.*;
import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_REGISTERED;

@Configuration
public class AuctionKafkaDltTopicsConfig {

    private static final String DLT = "-dlt";

    @Bean
    KafkaAdmin.NewTopics auctionDltTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(PRODUCT_REGISTERED + DLT).build(),
                TopicBuilder.name(ORDER_PAYMENT_SUCCEED + DLT).build(),
                TopicBuilder.name(CART_PAYMENT_DEFAULT + DLT).build(),
                TopicBuilder.name(ORDER_CONFIRM_PURCHASE + DLT).build(),
                TopicBuilder.name(ORDER_REFUND_ACCEPTED + DLT).build()
        );
    }
}