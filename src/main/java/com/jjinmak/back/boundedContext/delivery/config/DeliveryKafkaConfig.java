package com.jjinmak.back.boundedContext.delivery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.back.shared.delivery.event.DeliveryEventTopics.*;

@Configuration
public class DeliveryKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics deliveryTopics(){
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(DELIVERY_CREATED).build(),
                TopicBuilder.name(DELIVERY_STATUS_UPDATED).build()
        );
    }
}
