package com.jjinmak.member.app;

public record AuthResult(
        String accessToken,
        String refreshToken,
        boolean suspended
) {}