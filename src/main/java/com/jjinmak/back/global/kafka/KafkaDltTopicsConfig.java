package com.jjinmak.back.global.kafka;

import com.jjinmak.back.shared.auction.event.AuctionEventTopics;
import com.jjinmak.back.shared.delivery.event.DeliveryEventTopics;
import com.jjinmak.back.shared.order.event.OrderEventTopics;
import com.jjinmak.back.shared.payment.event.PaymentEventTopics;
import com.jjinmak.back.shared.product.event.ProductEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaDltTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics dltTopics(){
        return new KafkaAdmin.NewTopics(
                // 주문
                TopicBuilder.name(OrderEventTopics.ORDER_CONFIRM_PURCHASE + "-dlt").build(),
                TopicBuilder.name(OrderEventTopics.ORDER_REFUND_REQUESTED + "-dlt").build(),
                TopicBuilder.name(OrderEventTopics.ORDER_GROUP_DELETED + "-dlt").build(),
                TopicBuilder.name(OrderEventTopics.ORDER_PAYMENT_SUCCEED + "-dlt").build(),
                TopicBuilder.name(OrderEventTopics.ORDER_REFUND_ACCEPTED + "-dlt").build(),
                TopicBuilder.name(OrderEventTopics.ORDER_SEND_PRODUCT + "-dlt").build(),
                TopicBuilder.name(OrderEventTopics.CART_PAYMENT_DEFAULT + "-dlt").build(),

                // 배송
                TopicBuilder.name(DeliveryEventTopics.DELIVERY_CREATED + "-dlt").build(),
                TopicBuilder.name(DeliveryEventTopics.DELIVERY_STATUS_UPDATED + "-dlt").build(),

                // 경매
                TopicBuilder.name(AuctionEventTopics.AUCTION_STARTED + "-dlt").build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_WON + "-dlt").build(),
                TopicBuilder.name(AuctionEventTopics.AUCTION_FAILED + "-dlt").build(),

                // 결제
                TopicBuilder.name(PaymentEventTopics.PAYMENT_PAID_EVENT + "-dlt").build(),
                TopicBuilder.name(PaymentEventTopics.PAYMENT_REFUND_EVENT + "-dlt").build(),
                TopicBuilder.name(PaymentEventTopics.PAYMENT_RELEASED_EVENT + "-dlt").build()
          
                // 상품
                TopicBuilder.name(ProductEventTopics.PRODUCT_REGISTERED + "-dlt").build()
        );
    }
}
