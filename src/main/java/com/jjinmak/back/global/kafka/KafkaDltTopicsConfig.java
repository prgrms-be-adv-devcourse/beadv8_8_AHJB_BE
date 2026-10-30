package com.jjinmak.back.global.kafka;

import com.jjinmak.back.shared.order.event.OrderEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaDltTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics dltTopics(){
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(OrderEventTopics.ORDER_CONFIRM_PURCHASE + "-dlt").build(),
                TopicBuilder.name(OrderEventTopics.ORDER_REFUND_REQUESTED + "-dlt").build()
        );
    }
}
