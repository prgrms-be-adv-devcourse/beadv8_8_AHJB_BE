package com.jjinmak.back.shared.payment.dto;

import java.util.UUID;


public record WalletMemberDto(
        Long id,
        UUID uuid
) {
}
