package com.jjinmak.back.boundedContext.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 토스페이먼츠 코어 API 용 RestClient.
 *
 * 인증: 시크릿 키 뒤에 ':' 를 붙여 Base64 인코딩한 Basic 헤더. (test_sk_... 로 시작하는 테스트 키로 연동 테스트)
 * 읽기 타임아웃 60초: 거래 조회(GET /v1/transactions)가 최대 60초 걸릴 수 있다는 토스 문서 기준.
 *
 * 환경 변수
 *  - TOSS_PAYMENTS_SECRET_KEY : 토스 개발자센터 > API 키 > 시크릿 키
 *  - TOSS_PAYMENTS_BASE_URL   : 기본 https://api.tosspayments.com
 */
@Configuration
public class TossPaymentsRestClientConfig {

    @Value("${TOSS_PAYMENTS_BASE_URL:https://api.tosspayments.com}")
    private String baseUrl;

    @Value("${TOSS_PAYMENTS_SECRET_KEY:}")
    private String secretKey;

    @Bean
    public RestClient tossPaymentsRestClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        String basic = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
