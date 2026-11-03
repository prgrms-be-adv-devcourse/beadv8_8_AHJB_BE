package com.jjinmak.back.boundedContext.refund.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.back.shared.refund.event.RefundEventTopics.*;

@Configuration
public class RefundKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics refundTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(REFUND_ACCEPTED).build(),
                TopicBuilder.name(REFUND_REJECTED).build(),
                TopicBuilder.name(REFUND_RETRY_REQUESTED).build(),
                TopicBuilder.name(REFUND_COMPLETED).build()
        );
    }
}
