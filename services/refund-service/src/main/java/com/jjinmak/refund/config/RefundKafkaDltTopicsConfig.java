package com.jjinmak.refund.config;

import com.jjinmak.contracts.order.event.OrderEventTopics;
import com.jjinmak.contracts.payment.event.PaymentEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

// refund 서비스가 구독하는 토픽의 DLT. 재시도 3회 실패 시 여기로 보관된다
@Configuration
public class RefundKafkaDltTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics dltTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(dlt(OrderEventTopics.ORDER_REFUND_REQUESTED)).build(),
                TopicBuilder.name(dlt(PaymentEventTopics.PAYMENT_REFUND_EVENT)).build()
        );
    }

    private String dlt(String topic) {
        return topic + "-dlt";
    }
}
