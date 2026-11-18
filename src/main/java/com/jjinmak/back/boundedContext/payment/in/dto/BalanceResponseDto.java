package com.jjinmak.back.boundedContext.payment.in.dto;

import com.jjinmak.back.boundedContext.payment.app.dto.WalletBalanceDto;

/** 내 잔액 응답. balance = 예치금(결제에 쓸 수 있는 돈), settlementBalance = 정산금(판매 대금, 송금 대기) */
public record BalanceResponseDto(
        long balance,
        long settlementBalance
) {
    public static BalanceResponseDto from(WalletBalanceDto dto) {
        return new BalanceResponseDto(dto.balance(), dto.settlementBalance());
    }
}
