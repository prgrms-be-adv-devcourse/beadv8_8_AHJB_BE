package com.jjinmak.back.shared.payment.dto;


public record ChargeDto (
        Long holderId,
        long amount,
        Long paymentId
){
}
