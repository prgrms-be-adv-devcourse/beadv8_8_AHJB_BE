package com.jjinmak.back.shared.member.event;

import com.jjinmak.back.shared.member.dto.MemberWarningDto;

public record MemberWarningEvent(
        MemberWarningDto warning
) {}
