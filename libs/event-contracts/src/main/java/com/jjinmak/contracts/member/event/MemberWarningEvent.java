package com.jjinmak.contracts.member.event;

import com.jjinmak.contracts.member.dto.MemberWarningDto;

public record MemberWarningEvent(
        MemberWarningDto warning
) {}
