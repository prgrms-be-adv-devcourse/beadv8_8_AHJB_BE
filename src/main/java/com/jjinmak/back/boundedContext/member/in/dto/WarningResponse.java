package com.jjinmak.back.boundedContext.member.in.dto;

import java.time.LocalDateTime;

public record WarningResponse(
        Long warningId,
        String reason,
        Long orderId,                    // 신고 등 주문과 무관하면 null
        LocalDateTime createdAt
) {}