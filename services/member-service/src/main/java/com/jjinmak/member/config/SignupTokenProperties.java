package com.jjinmak.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 소셜 가입 대기 토큰 설정.
 * secret 은 액세스 토큰(RS256) 키와 반드시 다른 값이어야 한다. 게이트웨이가 이 토큰을 액세스 토큰으로 받아주지 않게 하기 위함.
 * HS256 이므로 최소 32바이트(256비트) 이상이어야 하며, 짧으면 기동 시 예외가 난다.
 */
@ConfigurationProperties(prefix = "oauth.signup-token")
public record SignupTokenProperties(
        String secret,
        Duration expiration
) {
}
