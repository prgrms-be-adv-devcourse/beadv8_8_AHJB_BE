package com.jjinmak.back.boundedContext.refund.in;

import com.jjinmak.back.shared.refund.dto.RefundDto;

public record RefundCompletedEvent(
        RefundDto refund
) {
}