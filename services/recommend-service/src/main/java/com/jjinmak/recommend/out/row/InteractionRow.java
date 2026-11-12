package com.jjinmak.recommend.out.row;

import java.time.LocalDateTime;

public record InteractionRow(
        Long productId,
        String type,
        LocalDateTime lastOccurredAt
) {
}