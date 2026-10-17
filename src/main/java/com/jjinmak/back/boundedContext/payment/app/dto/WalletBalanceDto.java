package com.jjinmak.back.boundedContext.payment.app.dto;


/** 지갑 잔액. balance 는 예치금, settlementBalance 는 정산금 */
public record WalletBalanceDto(
        Long id,
        long balance,
        long settlementBalance
) {
}
