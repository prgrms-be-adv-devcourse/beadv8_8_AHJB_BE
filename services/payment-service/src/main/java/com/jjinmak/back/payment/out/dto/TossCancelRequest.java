package com.jjinmak.back.payment.out.dto;

import com.fasterxml.jackson.annotation.JsonInclude;


// cancelAmount 가 없으면 전액 취소, 있으면 그 금액만 부분 취소
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TossCancelRequest(
        String cancelReason,
        Long cancelAmount
) {
    public TossCancelRequest(String cancelReason) {
        this(cancelReason, null);
    }
}
