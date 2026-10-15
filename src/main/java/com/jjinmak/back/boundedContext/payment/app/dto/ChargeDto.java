package com.jjinmak.back.boundedContext.payment.app.dto;


public record ChargeDto (
        Long holderId,
        long amount,
        Long paymentId
){
}
