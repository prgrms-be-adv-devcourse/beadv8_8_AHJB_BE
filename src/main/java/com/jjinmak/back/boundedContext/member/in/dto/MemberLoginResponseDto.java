package com.jjinmak.back.boundedContext.member.in.dto;

public record MemberLoginResponseDto(
        String accessToken,
        String refreshToken,
        String nickname,
        boolean suspended
){}
