package com.jjinmak.back.boundedContext.payment.out.dto;

/** POST /v1/payments/{paymentKey}/cancel 요청 본문. cancelAmount 를 생략하면 전액 취소 */
public record TossCancelRequest(
        String cancelReason
) {
}
