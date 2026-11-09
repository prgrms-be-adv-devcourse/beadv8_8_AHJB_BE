package com.jjinmak.order.config;

import com.jjinmak.contracts.auction.event.AuctionEventTopics;
import com.jjinmak.contracts.delivery.event.DeliveryEventTopics;
import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.payment.event.PaymentEventTopics;
import com.jjinmak.contracts.refund.event.RefundEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class OrderKafkaDltTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics dltTopics() {
        return new KafkaAdmin.NewTopics(
                // member
                TopicBuilder.name(dlt(MemberEventTopics.MEMBER_CREATED)).build(),

                // auction
                TopicBuilder.name(dlt(AuctionEventTopics.AUCTION_WON)).build(),

                // delivery
                TopicBuilder.name(dlt(DeliveryEventTopics.DELIVERY_CREATED)).build(),
                TopicBuilder.name(dlt(DeliveryEventTopics.DELIVERY_STATUS_UPDATED)).build(),

                // payment
                TopicBuilder.name(dlt(PaymentEventTopics.PAYMENT_PAID_EVENT)).build(),

                // refund
                TopicBuilder.name(dlt(RefundEventTopics.REFUND_ACCEPTED)).build(),
                TopicBuilder.name(dlt(RefundEventTopics.REFUND_REJECTED)).build()
        );
    }

    private String dlt(String topic){
        return topic + "-dlt";
    }
}
