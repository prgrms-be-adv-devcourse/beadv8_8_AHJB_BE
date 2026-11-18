package com.jjinmak.back.boundedContext.member.app;

public record AuthResult(
        String accessToken,
        String refreshToken,
        boolean suspended
) {}