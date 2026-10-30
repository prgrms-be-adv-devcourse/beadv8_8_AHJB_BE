package com.jjinmak.back.shared.refund.event;

import com.jjinmak.back.shared.refund.dto.RefundDto;

public record RefundRetryRequestedEvent(
        RefundDto refund
) {
}