package com.jjinmak.boundedcontext.market.shared.config;

import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.order.event.OrderEventTopics;
import com.jjinmak.contracts.product.event.ProductEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

// market(상품·경매·후기)이 쓰는 Kafka 토픽 선언.
// 토픽 자동 생성이 꺼져 있어서 발행 토픽과 DLT를 직접 선언한다. 이미 있는 토픽이면 KafkaAdmin이 그대로 둔다.
@Configuration
public class MarketKafkaConfig {

    private static final String DLT = "-dlt";

    // market이 발행하는 토픽
    @Bean
    KafkaAdmin.NewTopics marketTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(ProductEventTopics.PRODUCT_REGISTERED).build(),
                TopicBuilder.name(ProductEventTopics.PRODUCT_CHANGED).build(),
                TopicBuilder.name(ProductEventTopics.PRODUCT_WISH_CHANGED).build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_STARTED).build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_WON).build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_FAILED).build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_BID_SUCCEED).build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_ENDING_SOON).build()
        );
    }

    @Bean
    KafkaAdmin.NewTopics marketDltTopics() {
        return new KafkaAdmin.NewTopics(
                // 발행: 구독 측이 처리에 실패하면 여기로 간다
                TopicBuilder.name(ProductEventTopics.PRODUCT_REGISTERED + DLT).build(),
                TopicBuilder.name(ProductEventTopics.PRODUCT_CHANGED + DLT).build(),

                // 구독: market이 처리에 실패하면 여기로 간다
                TopicBuilder.name(MemberEventTopics.MEMBER_CREATED + DLT).build(),
                TopicBuilder.name(OrderEventTopics.ORDER_PAYMENT_SUCCEED + DLT).build(),
                TopicBuilder.name(OrderEventTopics.CART_PAYMENT_DEFAULT + DLT).build(),
                TopicBuilder.name(OrderEventTopics.ORDER_CONFIRM_PURCHASE + DLT).build(),
                TopicBuilder.name(OrderEventTopics.ORDER_REFUND_ACCEPTED + DLT).build()
        );
    }
}
