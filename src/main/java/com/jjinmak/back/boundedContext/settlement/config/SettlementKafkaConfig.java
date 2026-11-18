package com.jjinmak.back.boundedContext.settlement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.back.shared.settlement.event.SettlementEventTopics.*;

@Configuration
public class SettlementKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics settlementTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(SETTLEMENT_REQUESTED).build()
        );
    }
}