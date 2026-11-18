package com.jjinmak.back.boundedContext.order.app.dto;

public record PaymentResponseDto(
        Long orderGroupId,
        boolean isPaid,
        Data data
) {
    public record Data(
            Long paymentId,
            long totalPrice,
            long chargeAmount,
            String pgOrderId,
            String orderName
    ){}
}
