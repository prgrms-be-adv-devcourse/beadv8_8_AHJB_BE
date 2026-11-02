package com.jjinmak.back.boundedContext.auction.config;

import com.jjinmak.back.shared.auction.event.AuctionEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class AuctionKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics auctionTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(AuctionEventTopics.AUCTION_STARTED).build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_WON).build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_FAILED).build()
        );
    }
}