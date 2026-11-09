package com.jjinmak.search.config;

import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.product.event.ProductEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

// 토픽 자동 생성이 꺼져 있어서, 이 서비스가 구독하는 토픽의 DLT는 직접 선언한다.
// 이미 있는 토픽이면 KafkaAdmin이 그대로 둔다.
@Configuration
public class SearchKafkaDltTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics searchDltTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(ProductEventTopics.PRODUCT_CHANGED + "-dlt").build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_STARTED + "-dlt").build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_WON + "-dlt").build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_FAILED + "-dlt").build()
        );
    }
}
