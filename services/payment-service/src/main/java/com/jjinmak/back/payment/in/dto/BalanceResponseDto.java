package com.jjinmak.back.payment.in.dto;

import com.jjinmak.back.payment.app.dto.WalletBalanceDto;


public record BalanceResponseDto(
        long balance,
        long settlementBalance
) {
    public static BalanceResponseDto from(WalletBalanceDto dto) {
        return new BalanceResponseDto(dto.balance(), dto.settlementBalance());
    }
}
