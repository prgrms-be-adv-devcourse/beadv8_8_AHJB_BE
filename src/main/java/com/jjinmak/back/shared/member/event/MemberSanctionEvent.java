package com.jjinmak.back.shared.member.event;

import com.jjinmak.back.shared.member.dto.MemberSanctionDto;

public record MemberSanctionEvent(
        MemberSanctionDto memberSanction
)
{}
