package com.jjinmak.back.order.app.dto;

import com.jjinmak.back.order.domain.OrderOutbox;

import java.util.List;
import java.util.Map;

public record OutboxSendResult(
        List<Long> sentIds,
        Map<OrderOutbox, Throwable> failures
) {
}
