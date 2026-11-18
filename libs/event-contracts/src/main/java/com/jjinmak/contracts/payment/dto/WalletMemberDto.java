package com.jjinmak.contracts.payment.dto;

import java.util.UUID;


public record WalletMemberDto(
        Long id,
        UUID uuid
) {
}
