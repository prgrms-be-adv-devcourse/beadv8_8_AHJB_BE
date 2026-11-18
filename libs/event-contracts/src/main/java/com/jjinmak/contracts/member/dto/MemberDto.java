package com.jjinmak.contracts.member.dto;


import java.util.UUID;

public record MemberDto(
        Long id,
        UUID uuid,
        String nickname
) {
}
