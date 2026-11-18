package com.jjinmak.back.boundedContext.member.in;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Optional;

@Component
public class RefreshTokenCookieManager {

    private final String name;
    private final String path;
    private final String sameSite;
    private final boolean secure;
    private final Duration maxAge;

    public RefreshTokenCookieManager(
            @Value("${auth.refresh-cookie.name}") String name,
            @Value("${auth.refresh-cookie.path}") String path,
            @Value("${auth.refresh-cookie.same-site}") String sameSite,
            @Value("${auth.refresh-cookie.secure}") boolean secure,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.name = name;
        this.path = path;
        this.sameSite = sameSite;
        this.secure = secure;
        this.maxAge = Duration.ofMillis(refreshTokenExpiration);
    }

    public ResponseCookie create(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(maxAge)
                .build();
    }

    // 브라우저에 저장된 쿠키를 지우기 위해 같은 이름,경로로 만료된 쿠키를 내려준다
    public ResponseCookie expire() {
        return baseCookie("")
                .maxAge(0)
                .build();
    }

    public Optional<String> resolve(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, name))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path);
    }
}