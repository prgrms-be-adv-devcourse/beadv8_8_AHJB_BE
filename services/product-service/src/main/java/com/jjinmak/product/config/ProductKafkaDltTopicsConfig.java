package com.jjinmak.product.config;

import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.order.event.OrderEventTopics;
import com.jjinmak.contracts.product.event.ProductEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

// 토픽 자동 생성이 꺼져 있어서, 이 서비스가 쓰는 DLT는 직접 선언한다.
// 이미 있는 토픽이면 KafkaAdmin이 그대로 둔다.
@Configuration
public class ProductKafkaDltTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics productDltTopics() {
        return new KafkaAdmin.NewTopics(
                // 발행: 구독 측이 처리에 실패하면 여기로 간다
                TopicBuilder.name(ProductEventTopics.PRODUCT_REGISTERED + "-dlt").build(),
                TopicBuilder.name(ProductEventTopics.PRODUCT_CHANGED + "-dlt").build(),

                // 구독: 상품·후기가 처리에 실패하면 여기로 간다
                TopicBuilder.name(MemberEventTopics.MEMBER_CREATED + "-dlt").build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_STARTED + "-dlt").build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_WON + "-dlt").build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_FAILED + "-dlt").build(),
                TopicBuilder.name(OrderEventTopics.ORDER_CONFIRM_PURCHASE + "-dlt").build()
        );
    }
}
