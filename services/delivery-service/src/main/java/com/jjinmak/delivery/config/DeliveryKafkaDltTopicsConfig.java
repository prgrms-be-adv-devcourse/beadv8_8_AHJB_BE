package com.jjinmak.delivery.config;

import com.jjinmak.contracts.member.event.MemberEventTopics;
import com.jjinmak.contracts.order.event.OrderEventTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class DeliveryKafkaDltTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics dltTopics() {
        return new KafkaAdmin.NewTopics(
                // member
                TopicBuilder.name(dlt(MemberEventTopics.MEMBER_CREATED)).build(),

                // order
                TopicBuilder.name(dlt(OrderEventTopics.ORDER_SEND_PRODUCT)).build()
        );
    }

    private String dlt(String topic){
        return topic + "-dlt";
    }
}
