package com.jjinmak.contracts.member.event;

import com.jjinmak.contracts.member.dto.MemberSanctionDto;

public record MemberSanctionEvent(
        MemberSanctionDto memberSanction
)
{}
