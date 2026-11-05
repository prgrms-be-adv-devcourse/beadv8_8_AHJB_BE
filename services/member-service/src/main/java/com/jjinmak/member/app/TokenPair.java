package com.jjinmak.member.app;

public record TokenPair(
        String accessToken,
        String refreshToken
){}
