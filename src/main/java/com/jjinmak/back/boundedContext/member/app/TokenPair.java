package com.jjinmak.back.boundedContext.member.app;

public record TokenPair(
        String accessToken,
        String refreshToken
){}
