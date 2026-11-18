package com.jjinmak.back.shared.member.event;

import com.jjinmak.back.shared.member.dto.MemberDto;

public record MemberCreatedEvent(
        MemberDto member
) {
}
