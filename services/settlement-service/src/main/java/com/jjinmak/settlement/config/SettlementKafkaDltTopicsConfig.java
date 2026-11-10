package com.jjinmak.settlement.config;

import com.jjinmak.contracts.order.event.OrderEventTopics;
import com.jjinmak.contracts.payment.event.PaymentEventTopics;
import com.jjinmak.contracts.product.event.ProductEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

// settlement 서비스가 구독하는 토픽의 DLT. 재시도 3회 실패 시 여기로 보관된다
@Configuration
public class SettlementKafkaDltTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics dltTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(dlt(ProductEventTopics.PRODUCT_REGISTERED)).build(),
                TopicBuilder.name(dlt(OrderEventTopics.ORDER_CONFIRM_PURCHASE)).build(),
                TopicBuilder.name(dlt(PaymentEventTopics.PAYMENT_RELEASED_EVENT)).build()
        );
    }

    private String dlt(String topic) {
        return topic + "-dlt";
    }
}
