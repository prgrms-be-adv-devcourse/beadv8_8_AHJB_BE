package com.jjinmak.member.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({KakaoOAuthProperties.class, SignupTokenProperties.class})
public class OAuthConfig {

    /**
     * 카카오 API 호출용 클라이언트.
     * 타임아웃이 없으면 카카오 응답이 늦을 때 요청 스레드가 계속 묶여 일반 로그인 등 다른 요청까지 처리하지 못하게 된다.
     */
    @Bean
    public RestClient kakaoRestClient(KakaoOAuthProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
