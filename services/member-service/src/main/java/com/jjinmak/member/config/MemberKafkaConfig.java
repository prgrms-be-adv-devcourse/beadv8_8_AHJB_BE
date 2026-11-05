package com.jjinmak.member.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import static com.jjinmak.contracts.member.event.MemberEventTopics.MEMBER_CREATED;
import static com.jjinmak.contracts.member.event.MemberEventTopics.MEMBER_SANCTIONED;

@Configuration
public class MemberKafkaConfig {

    @Bean
    KafkaAdmin.NewTopics orderTopics(){
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(MEMBER_CREATED).compact().build(),
                TopicBuilder.name(MEMBER_SANCTIONED).build()
        );
    }

}
