package com.jjinmak.back.boundedContext.delivery.out.deliveryTracker;

import io.github.bucket4j.Bucket;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(DeliveryTrackerProperties.class)
public class DeliveryTrackerClientConfig {

    @Bean
    public RestClient deliveryTrackerRestClient(DeliveryTrackerProperties properties) {
        DeliveryTrackerProperties.RateLimit limit = properties.rateLimit();

        Bucket bucket = Bucket.builder()
                .addLimit(l -> l
                        .capacity(limit.capacity())
                        .refillGreedy(limit.refillTokens(), limit.refillPeriod()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))   // 연결 타임아웃
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));    // 응답 대기 타임아웃

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "TRACKQL-API-KEY %s:%s".formatted(properties.clientId(), properties.clientSecret()))
                .requestInterceptor(new DeliveryTrackerRateLimitInterceptor(bucket, limit.maxWait()))
                .build();
    }
}