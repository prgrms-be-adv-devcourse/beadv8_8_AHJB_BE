package com.jjinmak.back.payment.app.dto;


public record WalletBalanceDto(
        Long id,
        long balance,
        long settlementBalance,
        long pointBalance
) {
}
