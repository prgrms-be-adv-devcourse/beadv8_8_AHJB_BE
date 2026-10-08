package com.jjinmak.back.shared.member.dto;


import java.util.UUID;

public record MemberDto(
        Long id,
        UUID uuid,
        String nickname
) {
}
