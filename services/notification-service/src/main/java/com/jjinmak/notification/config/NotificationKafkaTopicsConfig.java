package com.jjinmak.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_WON;

@Configuration
public class NotificationKafkaTopicsConfig {

    private static final String DLT = "-dlt";

    @Bean
    KafkaAdmin.NewTopics notificationTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(AUCTION_WON).build(),
                TopicBuilder.name(AUCTION_WON + DLT).build()
        );
    }
}