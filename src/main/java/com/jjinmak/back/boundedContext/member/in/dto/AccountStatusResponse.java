package com.jjinmak.back.boundedContext.member.in.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AccountStatusResponse(
        String accountStatus,     // ACTIVE | SUSPENDED | BANNED
        int warningCount,
        LocalDateTime suspendedUntil,    // 정지 상태가 아니면 null
        List<WarningResponse> warnings   // 최신순, 없으면 빈 배열 []
) {}