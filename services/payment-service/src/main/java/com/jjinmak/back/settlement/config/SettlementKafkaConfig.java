package com.jjinmak.back.settlement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.contracts.settlement.event.SettlementEventTopics.SETTLEMENT_REQUESTED;

@Configuration
public class SettlementKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics settlementTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(SETTLEMENT_REQUESTED).build()
        );
    }
}
