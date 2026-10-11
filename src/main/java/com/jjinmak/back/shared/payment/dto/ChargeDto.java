package com.jjinmak.back.shared.payment.dto;


public record ChargeDto (
        Long holderId,
        long pgAmount,
        Long paymentId
){
}
