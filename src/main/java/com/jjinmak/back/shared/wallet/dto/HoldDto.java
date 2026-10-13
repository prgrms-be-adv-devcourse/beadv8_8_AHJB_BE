package com.jjinmak.back.shared.wallet.dto;

import java.util.List;

public record HoldDto(
        Long paymentId,
        Long holderId,
        long totalPrice,
        List<Item> items
) {
    public record Item(
            Long orderId,
            Long sellerId,
            long winningPrice,
            long deliveryFee
    ) {}
}