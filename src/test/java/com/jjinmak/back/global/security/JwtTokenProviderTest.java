package com.jjinmak.back.global.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "dGhpcy1pcy1hLXZlcnktbG9uZy1zZWNyZXQta2V5LWZvci1qd3QtaHMyNTY=";

    JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            SECRET,
            86400000L * 30
    );

    @Test
    void 토큰_생성_후_파싱하면_같은_값이_나온다() {
        String token = jwtTokenProvider.createAccessToken(1L, List.of("ROLE_BUYER","PERM_TRADE"));
        Long memberId = jwtTokenProvider.getMemberId(token);
        System.out.println(token);
        assertThat(memberId).isEqualTo(1L);
    }
}
