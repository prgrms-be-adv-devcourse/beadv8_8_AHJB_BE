package com.jjinmak.member.in.dto;

public record MemberSummaryResponseDto(
        String nickname,
        boolean seller,
        boolean suspended
) {}