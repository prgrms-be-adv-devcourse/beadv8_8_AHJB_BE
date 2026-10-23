package com.jjinmak.back.shared.order.event;

public record OrderRefundRequestedEvent(
        Long orderId,
        Long winnerId,
        Long sellerId,
        Long productId,
        Long winningPrice,
        Long deliveryFee,
        RefundType refundType
) {
    public enum RefundType{
        WINNER_REQUEST,
        SYSTEM_REQUEST
    }

    public static OrderRefundRequestedEvent bySystem(
            Long orderId,
            Long winnerId,
            Long sellerId,
            Long productId,
            Long winningPrice,
            Long deliveryFee
    ){
        return new OrderRefundRequestedEvent(
                orderId,
                winnerId,
                sellerId,
                productId,
                winningPrice,
                deliveryFee,
                RefundType.SYSTEM_REQUEST
        );
    }

    public static OrderRefundRequestedEvent byWinner(
            Long orderId,
            Long winnerId,
            Long sellerId,
            Long productId,
            Long winningPrice,
            Long deliveryFee
    ){
        return new OrderRefundRequestedEvent(
                orderId,
                winnerId,
                sellerId,
                productId,
                winningPrice,
                deliveryFee,
                RefundType.WINNER_REQUEST
        );
    }
}
