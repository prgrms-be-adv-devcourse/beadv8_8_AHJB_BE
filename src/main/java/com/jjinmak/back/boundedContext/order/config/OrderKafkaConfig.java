package com.jjinmak.back.boundedContext.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.back.shared.order.event.OrderEventTopics.*;

@Configuration
public class OrderKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics orderTopics(){
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(ORDER_CONFIRM_PURCHASE).build(),
                TopicBuilder.name(ORDER_REFUND_REQUESTED).build(),
                TopicBuilder.name(ORDER_GROUP_DELETED).build(),
                TopicBuilder.name(ORDER_PAYMENT_SUCCEED).build(),
                TopicBuilder.name(ORDER_REFUND_ACCEPTED).build(),
                TopicBuilder.name(ORDER_SEND_PRODUCT).build(),
                TopicBuilder.name(CART_PAYMENT_DEFAULT).build()
        );
    }
}
