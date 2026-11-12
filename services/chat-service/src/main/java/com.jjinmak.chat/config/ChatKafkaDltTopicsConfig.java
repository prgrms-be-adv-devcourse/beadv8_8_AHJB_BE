package com.jjinmak.chat.config;

import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.product.event.ProductEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

// chat 서비스가 구독하는 토픽의 DLT. 재시도 3회 실패 시 여기로 보관된다
@Configuration
public class ChatKafkaDltTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics dltTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(dlt(AuctionEventTopics.AUCTION_WON)).build(),
                TopicBuilder.name(dlt(MemberEventTopics.MEMBER_CREATED)).build(),
                TopicBuilder.name(dlt(ProductEventTopics.PRODUCT_REGISTERED)).build()
        );
    }

    private String dlt(String topic) {
        return topic + "-dlt";
    }
}