package com.jjinmak.recommend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.*;
import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_CHANGED;

@Configuration
public class RecommendKafkaDltTopics {

    @Bean
    KafkaAdmin.NewTopics dltTopics(){
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(dlt(AUCTION_STARTED)).build(),
                TopicBuilder.name(dlt(AUCTION_WON)).build(),
                TopicBuilder.name(dlt(AUCTION_FAILED)).build(),
                TopicBuilder.name(dlt(PRODUCT_CHANGED)).build()
        );
    }

    private String dlt(String topic){
        return topic + "-dlt";
    }
}
