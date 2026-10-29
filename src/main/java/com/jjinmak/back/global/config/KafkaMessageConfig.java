package com.jjinmak.back.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;

@Configuration
public class KafkaMessageConfig {

    @Bean
    RecordMessageConverter kafkaMessageConverter(){
        return new ByteArrayJacksonJsonMessageConverter();
    }
}
