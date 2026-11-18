package com.jjinmak.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * redirect-uri 는 인가 요청 때 프론트가 쓴 값과 정확히 같아야 토큰 교환이 성공한다.
 * 클라이언트가 보낸 값을 쓰지 않고 서버 설정값만 사용한다(조작된 redirect-uri 로 코드가 교환되는 것을 막기 위해).
 */
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        Duration connectTimeout,
        Duration readTimeout
) {
}
