package com.jjinmak.product.product.config;

import com.jjinmak.contracts.product.event.ProductEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class ProductKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics productTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(ProductEventTopics.PRODUCT_REGISTERED).build()
        );
    }
}
