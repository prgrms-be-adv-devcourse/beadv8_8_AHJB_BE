package com.jjinmak.ai.global.config;

import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.product.event.ProductEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.contracts.auction.event.AuctionEventTopics.*;
import static com.jjinmak.contracts.auction.event.AuctionEventTopics.AUCTION_BID_SUCCEED;
import static com.jjinmak.contracts.member.event.MemberEventTopics.MEMBER_CREATED;
import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_CHANGED;
import static com.jjinmak.contracts.product.event.ProductEventTopics.PRODUCT_WISH_CHANGED;

@Configuration
public class AiKafkaDltTopics {

    private static final String DLT = "-dlt";

    @Bean
    KafkaAdmin.NewTopics searchDltTopics(){
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(MEMBER_CREATED + DLT).build(),
                TopicBuilder.name(AUCTION_STARTED + DLT).build(),
                TopicBuilder.name(AUCTION_WON + DLT).build(),
                TopicBuilder.name(AUCTION_FAILED + DLT).build(),
                TopicBuilder.name(PRODUCT_CHANGED + DLT).build(),
                TopicBuilder.name(AUCTION_BID_SUCCEED + DLT).build(),
                TopicBuilder.name(PRODUCT_WISH_CHANGED + DLT).build()
        );
    }
}
