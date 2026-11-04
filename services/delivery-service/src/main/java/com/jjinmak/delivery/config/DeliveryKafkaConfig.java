package com.jjinmak.delivery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.contracts.delivery.event.DeliveryEventTopics.DELIVERY_CREATED;
import static com.jjinmak.contracts.delivery.event.DeliveryEventTopics.DELIVERY_STATUS_UPDATED;

@Configuration
public class DeliveryKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics orderTopics(){
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(DELIVERY_CREATED).build(),
                TopicBuilder.name(DELIVERY_STATUS_UPDATED).build()
        );
    }
}
