package com.jjinmak.member.in.dto;

public record TokenResponseDto(
        String accessToken,
        boolean suspended
) {}