package com.jjinmak.back.boundedContext.payment.out.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET /v1/transactions 응답의 거래 1건. 승인 1건, 취소 1건마다 하나씩 생긴다.
 * 일 단위 대사에서 "우리 Charge 에 없는 승인/취소가 토스에 있는가"를 볼 때 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossTransactionResponse(
        String transactionKey,
        String paymentKey,
        String orderId,
        String status,
        long amount,
        String transactionAt
) {
}
