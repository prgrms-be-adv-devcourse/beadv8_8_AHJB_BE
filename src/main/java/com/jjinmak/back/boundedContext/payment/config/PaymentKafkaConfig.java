package com.jjinmak.back.boundedContext.payment.config;

import com.jjinmak.back.shared.payment.event.PaymentEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class PaymentKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics paymentTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(PaymentEventTopics.PAYMENT_PAID_EVENT).build(),
                TopicBuilder.name(PaymentEventTopics.PAYMENT_RELEASED_EVENT).build(),
                TopicBuilder.name(PaymentEventTopics.PAYMENT_REFUND_EVENT).build()
        );
    }
}
