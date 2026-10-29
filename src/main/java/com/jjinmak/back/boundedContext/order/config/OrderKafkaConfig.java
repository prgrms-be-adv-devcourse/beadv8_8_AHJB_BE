package com.jjinmak.back.boundedContext.order.config;

import com.jjinmak.back.shared.order.event.OrderEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class OrderKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics orderTopics(){
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(OrderEventTopics.ORDER_CONFIRM_PURCHASE).build(),
                TopicBuilder.name(OrderEventTopics.ORDER_REFUND_REQUESTED).build()
        );
    }
}
