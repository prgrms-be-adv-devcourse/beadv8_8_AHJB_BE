package com.jjinmak.back.boundedContext.member.in.dto;

public record TokenResponseDto(
        String accessToken,
        boolean suspended
) {}