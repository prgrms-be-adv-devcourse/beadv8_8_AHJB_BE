package com.jjinmak.back.shared.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;


public record WalletBalanceDto(
        Long id,
        long balance
) {
}
