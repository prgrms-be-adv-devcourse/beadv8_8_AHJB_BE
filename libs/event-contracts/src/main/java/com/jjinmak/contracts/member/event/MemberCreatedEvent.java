package com.jjinmak.contracts.member.event;

import com.jjinmak.contracts.member.dto.MemberDto;

public record MemberCreatedEvent(
        MemberDto member
) {
}
