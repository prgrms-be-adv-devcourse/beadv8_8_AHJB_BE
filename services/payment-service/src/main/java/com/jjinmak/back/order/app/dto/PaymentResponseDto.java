package com.jjinmak.back.order.app.dto;

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
            String orderName,
            String pgMethod,
            String payMethod,
            long pointAmount,
            long depositAmount,
            long pgAmount
    ){}
}
