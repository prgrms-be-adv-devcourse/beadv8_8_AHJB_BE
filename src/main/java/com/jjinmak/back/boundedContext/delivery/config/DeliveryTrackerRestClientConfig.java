package com.jjinmak.back.boundedContext.delivery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class DeliveryTrackerRestClientConfig {

    @Value("${DELIVERY_TRACKER_BASE_URL:https://apis.tracker.delivery/graphql}")
    private String url;

    @Bean
    public RestClient deliveryTrackerRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))   // 연결 타임아웃
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(15));    // 응답 대기 타임아웃

        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}