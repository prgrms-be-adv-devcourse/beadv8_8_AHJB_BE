package com.jjinmak.back.boundedContext.member.in.dto;

public record MemberSummaryResponseDto(
        String nickname,
        boolean seller,
        boolean suspended
) {}