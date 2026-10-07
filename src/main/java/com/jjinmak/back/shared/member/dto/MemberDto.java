package com.jjinmak.back.shared.member.dto;

import java.util.UUID;

public record MemberDto(
        UUID uuid,
        String nickname
) {
}
